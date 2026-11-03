package com.goldstack.order;

import com.goldstack.common.Money;
import com.goldstack.common.error.ApiException;
import com.goldstack.order.OrderDtos.OrderCompletedEvent;
import com.goldstack.order.OrderDtos.OrderResponse;
import com.goldstack.order.OrderDtos.OrderResult;
import com.goldstack.price.PriceService;
import com.goldstack.wallet.WalletService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
class OrderExecutor {

    private final GoldOrderRepository orders;
    private final WalletService walletService;
    private final GoldService goldService;
    private final PriceService priceService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    OrderExecutor(GoldOrderRepository orders, WalletService walletService, GoldService goldService,
                  PriceService priceService, ApplicationEventPublisher events, Clock clock) {
        this.orders = orders;
        this.walletService = walletService;
        this.goldService = goldService;
        this.priceService = priceService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    OrderResult executeWithQuote(UUID userId, Quote quote, String idempotencyKey) {
        GoldOrder order = settle(userId, quote.side(), GoldOrder.Source.MANUAL, quote.quoteId(), idempotencyKey,
                quote.coins(), quote.grams(), quote.pricePerGram());
        return new OrderResult(OrderResponse.from(order), false);
    }

    @Transactional
    OrderResult executeSipBuy(UUID userId, BigDecimal coins, String idempotencyKey) {
        BigDecimal price = priceService.currentPrice().pricePerGram();
        BigDecimal amount = Money.coins(coins);
        BigDecimal grams = Money.gramsForCoins(amount, price);
        if (grams.signum() == 0) {
            throw ApiException.badRequest("AMOUNT_TOO_SMALL", "SIP amount is too small to buy any gold");
        }
        GoldOrder order = settle(userId, Side.BUY, GoldOrder.Source.SIP, null, idempotencyKey, amount, grams, price);
        return new OrderResult(OrderResponse.from(order), false);
    }

    private GoldOrder settle(UUID userId, Side side, GoldOrder.Source source, UUID quoteId, String key,
                             BigDecimal coins, BigDecimal grams, BigDecimal price) {
        UUID orderId = UUID.randomUUID();
        if (side == Side.BUY) {
            walletService.payForGold(userId, coins, orderId);
            goldService.deliverGold(userId, grams, orderId);
        } else {
            walletService.receiveGoldSaleProceeds(userId, coins, orderId);
            goldService.takeBackGold(userId, grams, orderId);
        }
        Instant now = Instant.now(clock);
        GoldOrder order = orders.save(GoldOrder.completed(orderId, userId, side, source, quoteId, key,
                coins, grams, price, now));
        events.publishEvent(new OrderCompletedEvent(orderId, userId, side, source.name(), coins, grams, price, now));
        return order;
    }
}
