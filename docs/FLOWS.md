# GoldStack flows (sequence diagrams)

Solid arrows are synchronous HTTP; dashed arrows are Kafka events. Every event a service publishes is first written to
its **outbox** table, in the same database transaction as its change, then relayed to Kafka. Every consumer
**skips events it has already processed** (by event id).

---

## 1. Buy: success

```mermaid
sequenceDiagram
    autonumber
    actor U as User (browser)
    participant G as api-gateway
    participant O as order-service
    participant R as Redis
    participant P as price-service
    participant K as Kafka
    participant W as wallet-service
    participant PF as portfolio / notification

    U->>G: POST /api/orders/quotes {BUY, coins: 500}
    G->>O: (JWT ok, rate limit ok)
    O->>P: GET /internal/prices/latest (Feign)
    O->>W: GET /internal/wallets/{id}/balance (Feign, friendly pre-check)
    O->>R: SET quote:{id} … EX 300
    O-->>U: 201 quote (price locked 5 min)

    U->>G: POST /api/orders/buy {quoteId} + Idempotency-Key
    G->>O: forward
    O->>R: SET idem:{user}:{key} PENDING NX  (one request per key)
    O->>R: GET + DEL quote:{id}  (single use)
    Note over O: TX: order PENDING + outbox(order.placed)
    O--)K: order.placed
    K--)W: order.placed
    Note over W: TX: lock wallet, debit 500 (ledger) + outbox(wallet.settled)
    W--)K: wallet.settled
    K--)O: wallet.settled
    Note over O: TX: credit gold (ledger), order COMPLETED + outbox(order.completed)
    O--)K: order.completed
    K--)PF: order.completed (update read model, notify)
    Note over O: POST /buy was polling its own DB (≤ 5 s)
    O-->>U: 201 {status: COMPLETED}
```

## 2. Buy: not enough coins (the saga stops, nothing to undo)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant O as order-service
    participant K as Kafka
    participant W as wallet-service
    participant N as notification

    U->>O: POST /api/orders/buy (via gateway)
    Note over O: TX: order PENDING + outbox(order.placed)
    O--)K: order.placed
    K--)W: order.placed
    Note over W: balance too low: nothing moves<br/>TX: outbox(wallet.settlement.failed)
    W--)K: wallet.settlement.failed {INSUFFICIENT_BALANCE}
    K--)O: wallet.settlement.failed
    Note over O: TX: order FAILED + outbox(order.failed, refundCoins=false)
    O--)K: order.failed
    K--)W: order.failed (refundCoins=false: no-op)
    K--)N: order.failed ("purchase failed")
    O-->>U: 422 INSUFFICIENT_BALANCE (quote put back; retry needs a NEW key)
```

## 3. Buy: gold credit fails after the coins were taken (COMPENSATION: refund)

Try it: `POST /api/dev/saga/fail-next-gold-credit`, then buy.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant O as order-service
    participant K as Kafka
    participant W as wallet-service
    participant N as notification

    U->>O: POST /api/orders/buy
    O--)K: order.placed
    K--)W: order.placed
    Note over W: debit 500 coins
    W--)K: wallet.settled
    K--)O: wallet.settled
    Note over O: gold credit FAILS<br/>TX: order FAILED + outbox(order.failed, refundCoins=true)
    O-->>U: 422 GOLD_CREDIT_FAILED ("coins are being refunded")
    O--)K: order.failed {refundCoins: true}
    K--)W: order.failed
    Note over W: COMPENSATION<br/>debited for this order and not refunded yet?<br/>TX: treasury → user, REFUND 500
    K--)N: order.failed ("your coins have been refunded")
```

## 4. Sell: success

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant O as order-service
    participant K as Kafka
    participant W as wallet-service
    participant PF as portfolio / notification

    U->>O: POST /api/orders/sell {quoteId} + Idempotency-Key
    Note over O: TX: lock gold, user → vault (step 1)<br/>order PENDING + outbox(order.placed)
    O--)K: order.placed {SELL}
    K--)W: order.placed
    Note over W: TX: treasury → user (coins) + outbox(wallet.settled)
    W--)K: wallet.settled
    K--)O: wallet.settled
    Note over O: TX: order COMPLETED + outbox(order.completed)
    O--)K: order.completed
    K--)PF: order.completed
    O-->>U: 201 COMPLETED
```

## 5. Sell: payout fails (COMPENSATION: gold returned)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant O as order-service
    participant K as Kafka
    participant W as wallet-service

    U->>O: POST /api/orders/sell
    Note over O: step 1: gold user → vault
    O--)K: order.placed {SELL}
    K--)W: order.placed
    Note over W: payout cannot be made
    W--)K: wallet.settlement.failed
    K--)O: wallet.settlement.failed
    Note over O: COMPENSATION<br/>TX: gold vault → user (SELL_REVERSAL)<br/>order FAILED + outbox(order.failed)
    O-->>U: 422 (gold is back in your account)
```

## 6. SIP installment: success

```mermaid
sequenceDiagram
    autonumber
    participant S as sip-service
    participant DB as sip_db (shedlock)
    participant K as Kafka
    participant O as order-service
    participant W as wallet-service
    participant N as notification

    Note over S: 09:00 @Scheduled fires on EVERY instance
    S->>DB: ShedLock: take "sipDailyRun" (only one instance wins)
    Note over S: for each due SIP, TX:<br/>execution PENDING (key sipId:date, UNIQUE)<br/>next_run_date += 1 cycle<br/>outbox(sip.debit.requested)
    S--)K: sip.debit.requested {key "sip:{sipId}:{date}"}
    K--)O: sip.debit.requested
    Note over O: order with that key exists? skip<br/>else price via Feign, order PENDING (source SIP)
    O--)K: order.placed
    K--)W: order.placed
    W--)K: wallet.settled
    K--)O: wallet.settled
    Note over O: credit gold, COMPLETED
    O--)K: order.completed {sipId, runDate}
    K--)S: order.completed → execution SUCCESS
    K--)N: "SIP installment: bought 0.0247 g"
```

## 7. SIP installment: not enough coins (retry next cycle)

```mermaid
sequenceDiagram
    autonumber
    participant S as sip-service
    participant K as Kafka
    participant O as order-service
    participant W as wallet-service
    participant N as notification

    S--)K: sip.debit.requested
    K--)O: sip.debit.requested
    O--)K: order.placed
    K--)W: order.placed
    Note over W: balance too low
    W--)K: wallet.settlement.failed
    K--)O: wallet.settlement.failed
    Note over O: order FAILED<br/>outbox(order.failed) + outbox(sip.debit.failed)
    O--)K: sip.debit.failed
    K--)S: execution FAILED (next_run_date was already moved on)
    K--)N: "SIP failed, we'll try again on the next scheduled date"
```
