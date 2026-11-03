package com.goldstack.wallet;

import com.goldstack.common.Side;
import com.goldstack.common.events.Events.OrderFailed;
import com.goldstack.common.events.Events.OrderPlaced;
import com.goldstack.common.events.Events.UserRegistered;
import com.goldstack.common.events.Topics;
import com.goldstack.common.outbox.IdempotentConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest
@Import(WalletSagaStepTest.Containers.class)
@ActiveProfiles("test")
class WalletSagaStepTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @Autowired IdempotentConsumer consumer;
    @Autowired WalletService wallet;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    @DisplayName("Two simultaneous 6,000-coin buys on a 10,000 balance: one settled, one rejected, never negative")
    void concurrentSettlementsCannotOverspend() throws Exception {
        UUID user = newUserWithBonus();
        OrderPlaced a = buy(user, "6000");
        OrderPlaced b = buy(user, "6000");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (OrderPlaced e : List.of(a, b)) {
            fs.add(pool.submit(() -> {
                go.await();
                consumer.handle(Topics.ORDER_PLACED, json(e), OrderPlaced.class, wallet::settle);
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : fs) {
            f.get();
        }
        pool.shutdown();

        assertThat(wallet.balanceOf(user)).isEqualByComparingTo("4000.00");
        assertThat(outboxCount(Topics.WALLET_SETTLED, a, b)).isEqualTo(1);
        assertThat(outboxCount(Topics.WALLET_SETTLEMENT_FAILED, a, b)).isEqualTo(1);
        assertThat(wallet.reconcile().balanced()).isTrue();
    }

    @Test
    @DisplayName("The same order.placed delivered twice is processed once (idempotent consumer)")
    void duplicateDeliveryIsIgnored() {
        UUID user = newUserWithBonus();
        String payload = json(buy(user, "500"));

        consumer.handle(Topics.ORDER_PLACED, payload, OrderPlaced.class, wallet::settle);
        consumer.handle(Topics.ORDER_PLACED, payload, OrderPlaced.class, wallet::settle);

        assertThat(wallet.balanceOf(user)).isEqualByComparingTo("9500.00");
    }

    @Test
    @DisplayName("Compensation: order.failed(refundCoins) returns the coins exactly once")
    void refundIsCompensationAndIdempotent() {
        UUID user = newUserWithBonus();
        OrderPlaced placed = buy(user, "700");
        consumer.handle(Topics.ORDER_PLACED, json(placed), OrderPlaced.class, wallet::settle);
        assertThat(wallet.balanceOf(user)).isEqualByComparingTo("9300.00");

        OrderFailed failed = new OrderFailed(UUID.randomUUID(), Instant.now(), placed.orderId(), user, Side.BUY,
                "MANUAL", placed.coins(), placed.grams(), "GOLD_CREDIT_FAILED", "test", true);
        consumer.handle(Topics.ORDER_FAILED, json(failed), OrderFailed.class, wallet::refund);

        OrderFailed again = new OrderFailed(UUID.randomUUID(), Instant.now(), placed.orderId(), user, Side.BUY,
                "MANUAL", placed.coins(), placed.grams(), "GOLD_CREDIT_FAILED", "test", true);
        consumer.handle(Topics.ORDER_FAILED, json(again), OrderFailed.class, wallet::refund);

        assertThat(wallet.balanceOf(user)).isEqualByComparingTo("10000.00");
        assertThat(wallet.reconcile().balanced()).isTrue();
    }

    private UUID newUserWithBonus() {
        UUID user = UUID.randomUUID();
        UserRegistered e = new UserRegistered(UUID.randomUUID(), Instant.now(), user, user + "@t.com", "T");
        consumer.handle(Topics.USER_REGISTERED, json(e), UserRegistered.class, wallet::grantSignupBonus);
        return user;
    }

    private static OrderPlaced buy(UUID user, String coins) {
        return new OrderPlaced(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), user, Side.BUY, "MANUAL",
                new BigDecimal(coins + ".00"), new BigDecimal("0.4000"), new BigDecimal("12000.00"), null, null);
    }

    private long outboxCount(String topic, OrderPlaced... orders) {
        long n = 0;
        for (OrderPlaced o : orders) {
            n += jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE topic = ? AND message_key = ?", Long.class,
                    topic, o.orderId().toString());
        }
        return n;
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
