package com.goldstack.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldstack.common.Side;
import com.goldstack.common.events.Events.WalletSettled;
import com.goldstack.common.events.Events.WalletSettlementFailed;
import com.goldstack.common.events.Topics;
import com.goldstack.common.outbox.IdempotentConsumer;
import com.goldstack.order.Clients.PriceClient;
import com.goldstack.order.Clients.WalletClient;
import com.goldstack.order.OrderDtos.CreateQuoteRequest;
import com.goldstack.order.OrderDtos.OrderResult;
import com.goldstack.order.OrderDtos.QuoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "goldstack.order.wait-ms=200")
@Import(OrderSagaTest.Containers.class)
@ActiveProfiles("test")
class OrderSagaTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }

        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redis() {
            return new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
        }
    }

    @MockitoBean PriceClient priceClient;
    @MockitoBean WalletClient walletClient;

    @Autowired QuoteService quoteService;
    @Autowired OrderService orderService;
    @Autowired OrderSaga saga;
    @Autowired GoldService goldService;
    @Autowired GoldOrderRepository orders;
    @Autowired IdempotentConsumer consumer;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void stubOtherServices() {
        when(priceClient.latest()).thenReturn(new PriceClient.LivePrice(new BigDecimal("10000.00"), Instant.now(), "TEST"));
        when(walletClient.balance(any())).thenReturn(new WalletClient.Balance(new BigDecimal("100000.00")));
    }

    @Test
    @DisplayName("BUY happy path: PENDING -> (wallet.settled) -> COMPLETED, gold credited, order.completed in outbox")
    void buyCompletesWhenWalletSettles() {
        UUID user = UUID.randomUUID();
        OrderResult placed = orderService.buy(user, buyQuote(user, "500").quoteId(), "k-" + UUID.randomUUID());
        assertThat(placed.order().status()).isEqualTo("PENDING");
        assertThat(outbox(Topics.ORDER_PLACED, user)).isEqualTo(1);

        walletSettled(placed.order().id(), user, Side.BUY);

        assertThat(orders.findById(placed.order().id()).orElseThrow().getStatus()).isEqualTo(GoldOrder.Status.COMPLETED);
        assertThat(goldService.gramsOf(user)).isEqualByComparingTo("0.0500");
        assertThat(outbox(Topics.ORDER_COMPLETED, user)).isEqualTo(1);
        assertThat(goldService.reconcile().balanced()).isTrue();
    }

    @Test
    @DisplayName("Same Idempotency-Key sent 5 times at once: exactly one order")
    void sameKeyConcurrentlyCreatesOneOrder() throws Exception {
        UUID user = UUID.randomUUID();
        UUID quoteId = buyQuote(user, "500").quoteId();
        String key = "same-" + UUID.randomUUID();

        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<OrderResult>> fs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            fs.add(pool.submit(() -> {
                go.await();
                return orderService.buy(user, quoteId, key);
            }));
        }
        go.countDown();
        List<UUID> ids = new ArrayList<>();
        for (Future<OrderResult> f : fs) {
            ids.add(f.get().order().id());
        }
        pool.shutdown();

        assertThat(ids).containsOnly(ids.get(0));
        assertThat(outbox(Topics.ORDER_PLACED, user)).isEqualTo(1);
    }

    @Test
    @DisplayName("BUY compensation: gold credit fails after the coins were taken -> FAILED + order.failed(refundCoins)")
    void buyGoldFailureAsksForRefund() {
        UUID user = UUID.randomUUID();
        OrderResult placed = orderService.buy(user, buyQuote(user, "500").quoteId(), "k-" + UUID.randomUUID());

        goldService.failNextGoldCredit();
        walletSettled(placed.order().id(), user, Side.BUY);

        GoldOrder o = orders.findById(placed.order().id()).orElseThrow();
        assertThat(o.getStatus()).isEqualTo(GoldOrder.Status.FAILED);
        assertThat(o.getFailureCode()).isEqualTo("GOLD_CREDIT_FAILED");
        assertThat(jdbc.queryForObject("SELECT payload FROM outbox WHERE topic = ? AND message_key = ?", String.class,
                Topics.ORDER_FAILED, user.toString())).contains("\"refundCoins\":true");
        assertThat(goldService.gramsOf(user)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("SELL compensation: wallet payout fails -> the gold taken in step 1 is given back")
    void sellPayoutFailureReturnsGold() {
        UUID user = UUID.randomUUID();
        OrderResult bought = orderService.buy(user, buyQuote(user, "1000").quoteId(), "b-" + UUID.randomUUID());
        walletSettled(bought.order().id(), user, Side.BUY);

        QuoteResponse sellQuote = quoteService.createQuote(user, new CreateQuoteRequest(Side.SELL, null, new BigDecimal("0.0400")));
        OrderResult sold = orderService.sell(user, sellQuote.quoteId(), "s-" + UUID.randomUUID());
        assertThat(goldService.gramsOf(user)).isEqualByComparingTo("0.0600");

        WalletSettlementFailed nope = new WalletSettlementFailed(UUID.randomUUID(), Instant.now(), sold.order().id(),
                user, Side.SELL, "WALLET_ERROR", "test failure");
        consumer.handle(Topics.WALLET_SETTLEMENT_FAILED, json(nope), WalletSettlementFailed.class, saga::onWalletSettlementFailed);

        assertThat(goldService.gramsOf(user)).isEqualByComparingTo("0.1000");
        assertThat(orders.findById(sold.order().id()).orElseThrow().getStatus()).isEqualTo(GoldOrder.Status.FAILED);
        assertThat(goldService.reconcile().balanced()).isTrue();
    }

    @Test
    @DisplayName("A duplicate wallet.settled is ignored (idempotent consumer + PENDING-only state machine)")
    void duplicateReplyIsIgnored() {
        UUID user = UUID.randomUUID();
        OrderResult placed = orderService.buy(user, buyQuote(user, "500").quoteId(), "k-" + UUID.randomUUID());
        WalletSettled e = new WalletSettled(UUID.randomUUID(), Instant.now(), placed.order().id(), user, Side.BUY);

        consumer.handle(Topics.WALLET_SETTLED, json(e), WalletSettled.class, saga::onWalletSettled);
        consumer.handle(Topics.WALLET_SETTLED, json(e), WalletSettled.class, saga::onWalletSettled);

        assertThat(goldService.gramsOf(user)).isEqualByComparingTo("0.0500");
    }

    private QuoteResponse buyQuote(UUID user, String coins) {
        return quoteService.createQuote(user, new CreateQuoteRequest(Side.BUY, new BigDecimal(coins), null));
    }

    private void walletSettled(UUID orderId, UUID user, Side side) {
        WalletSettled e = new WalletSettled(UUID.randomUUID(), Instant.now(), orderId, user, side);
        consumer.handle(Topics.WALLET_SETTLED, json(e), WalletSettled.class, saga::onWalletSettled);
    }

    private long outbox(String topic, UUID user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE topic = ? AND message_key = ?", Long.class,
                topic, user.toString());
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
