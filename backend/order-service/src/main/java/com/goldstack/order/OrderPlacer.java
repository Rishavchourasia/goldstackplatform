package com.goldstack.order;

import com.goldstack.common.Money;
import com.goldstack.common.Side;
import com.goldstack.common.events.Events.OrderPlaced;
import com.goldstack.common.events.Events.SipDebitFailed;
import com.goldstack.common.events.Events.SipDebitRequested;
import com.goldstack.common.events.Topics;
import com.goldstack.common.outbox.OutboxWriter;
import com.goldstack.order.Clients.PriceClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
class OrderPlacer {

    private final GoldOrderRepository orders;
    private final GoldService goldService;
    private final OutboxWriter outbox;
    private final PriceClient priceClient;
    private final Clock clock;

    OrderPlacer(GoldOrderRepository orders, GoldService goldService, OutboxWriter outbox, PriceClient priceClient,
                Clock clock) {
        this.orders = orders;
        this.goldService = goldService;
        this.outbox = outbox;
        this.priceClient = priceClient;
        this.clock = clock;
    }

    @Transactional
    public UUID placeBuy(UUID userId, Quote quote, String idempotencyKey) {
        GoldOrder order = GoldOrder.pending(userId, Side.BUY, GoldOrder.Source.MANUAL, quote.quoteId(), idempotencyKey,
                quote.coins(), quote.grams(), quote.pricePerGram(), null, null, Instant.now(clock));
        return saveAndAnnounce(order);
    }

    @Transactional
    public UUID placeSell(UUID userId, Quote quote, String idempotencyKey) {
        GoldOrder order = GoldOrder.pending(userId, Side.SELL, GoldOrder.Source.MANUAL, quote.quoteId(), idempotencyKey,
                quote.coins(), quote.grams(), quote.pricePerGram(), null, null, Instant.now(clock));
        goldService.takeBackGold(userId, quote.grams(), order.getId());
        return saveAndAnnounce(order);
    }

    @Transactional
    public void placeSip(SipDebitRequested e) {
        if (orders.existsByUserIdAndIdempotencyKey(e.userId(), e.idempotencyKey())) {
            return;
        }
        BigDecimal price = priceClient.latest().pricePerGram();
        BigDecimal coins = Money.coins(e.amount());
        BigDecimal grams = Money.gramsForCoins(coins, price);
        Instant now = Instant.now(clock);
        if (grams.signum() == 0) {
            outbox.write(Topics.SIP_DEBIT_FAILED, e.userId().toString(), new SipDebitFailed(UUID.randomUUID(), now,
                    e.sipId(), e.userId(), coins, e.runDate(), null, "AMOUNT_TOO_SMALL",
                    "SIP amount is too small to buy any gold"));
            return;
        }
        GoldOrder order = GoldOrder.pending(e.userId(), Side.BUY, GoldOrder.Source.SIP, null, e.idempotencyKey(),
                coins, grams, price, e.sipId(), e.runDate(), now);
        saveAndAnnounce(order);
    }

    private UUID saveAndAnnounce(GoldOrder order) {
        orders.save(order);
        outbox.write(Topics.ORDER_PLACED, order.getUserId().toString(), new OrderPlaced(UUID.randomUUID(),
                Instant.now(clock), order.getId(), order.getUserId(), order.getSide(), order.getSource().name(),
                order.getCoins(), order.getGrams(), order.getPricePerGram(), order.getSipId(), order.getRunDate()));
        return order.getId();
    }
}
