package com.goldstack.order;

import com.goldstack.common.Side;
import com.goldstack.common.events.Events.OrderCompleted;
import com.goldstack.common.events.Events.OrderFailed;
import com.goldstack.common.events.Events.SipDebitFailed;
import com.goldstack.common.events.Events.SipDebitRequested;
import com.goldstack.common.events.Events.WalletSettled;
import com.goldstack.common.events.Events.WalletSettlementFailed;
import com.goldstack.common.events.Topics;
import com.goldstack.common.outbox.IdempotentConsumer;
import com.goldstack.common.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
class OrderSaga {

    private static final Logger log = LoggerFactory.getLogger(OrderSaga.class);

    private final GoldOrderRepository orders;
    private final GoldService goldService;
    private final OutboxWriter outbox;
    private final Clock clock;

    OrderSaga(GoldOrderRepository orders, GoldService goldService, OutboxWriter outbox, Clock clock) {
        this.orders = orders;
        this.goldService = goldService;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void onWalletSettled(WalletSettled e) {
        GoldOrder order = orders.lockById(e.orderId()).orElse(null);
        if (order == null || !order.isPending()) {
            return;
        }
        if (order.getSide() == Side.BUY) {
            if (goldService.shouldFailThisGoldCredit()) {

                log.warn("Simulated gold-credit failure for order {}: compensating with a refund", order.getId());
                fail(order, "GOLD_CREDIT_FAILED", "We couldn't deliver the gold. Your coins are being refunded.", true);
                return;
            }
            goldService.deliverGold(order.getUserId(), order.getGrams(), order.getId());
        }
        complete(order);
    }

    @Transactional
    public void onWalletSettlementFailed(WalletSettlementFailed e) {
        GoldOrder order = orders.lockById(e.orderId()).orElse(null);
        if (order == null || !order.isPending()) {
            return;
        }
        if (order.getSide() == Side.SELL) {
            goldService.returnGold(order.getUserId(), order.getGrams(), order.getId());
        }
        fail(order, e.reasonCode(), e.reason(), false);
    }

    private void complete(GoldOrder order) {
        Instant now = Instant.now(clock);
        order.complete(now);
        outbox.write(Topics.ORDER_COMPLETED, order.getUserId().toString(), new OrderCompleted(UUID.randomUUID(), now,
                order.getId(), order.getUserId(), order.getSide(), order.getSource().name(), order.getCoins(),
                order.getGrams(), order.getPricePerGram(), order.getSipId(), order.getRunDate()));
    }

    private void fail(GoldOrder order, String code, String reason, boolean refundCoins) {
        Instant now = Instant.now(clock);
        order.fail(code, reason, now);
        outbox.write(Topics.ORDER_FAILED, order.getUserId().toString(), new OrderFailed(UUID.randomUUID(), now,
                order.getId(), order.getUserId(), order.getSide(), order.getSource().name(), order.getCoins(),
                order.getGrams(), code, reason, refundCoins));
        if (order.getSource() == GoldOrder.Source.SIP) {
            outbox.write(Topics.SIP_DEBIT_FAILED, order.getUserId().toString(), new SipDebitFailed(UUID.randomUUID(),
                    now, order.getSipId(), order.getUserId(), order.getCoins(), order.getRunDate(), order.getId(),
                    code, reason));
        }
    }
}

@Component
class OrderEventListeners {

    private final IdempotentConsumer consumer;
    private final OrderSaga saga;
    private final OrderPlacer placer;

    OrderEventListeners(IdempotentConsumer consumer, OrderSaga saga, OrderPlacer placer) {
        this.consumer = consumer;
        this.saga = saga;
        this.placer = placer;
    }

    @KafkaListener(topics = Topics.WALLET_SETTLED)
    void onWalletSettled(String payload) {
        consumer.handle(Topics.WALLET_SETTLED, payload, WalletSettled.class, saga::onWalletSettled);
    }

    @KafkaListener(topics = Topics.WALLET_SETTLEMENT_FAILED)
    void onWalletSettlementFailed(String payload) {
        consumer.handle(Topics.WALLET_SETTLEMENT_FAILED, payload, WalletSettlementFailed.class,
                saga::onWalletSettlementFailed);
    }

    @KafkaListener(topics = Topics.SIP_DEBIT_REQUESTED)
    void onSipDebitRequested(String payload) {
        consumer.handle(Topics.SIP_DEBIT_REQUESTED, payload, SipDebitRequested.class, placer::placeSip);
    }
}
