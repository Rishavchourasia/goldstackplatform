package com.goldstack.order;

import com.goldstack.TestContainers;
import com.goldstack.common.error.InsufficientFundsException;
import com.goldstack.order.OrderDtos.CreateQuoteRequest;
import com.goldstack.order.OrderDtos.OrderResult;
import com.goldstack.order.OrderDtos.QuoteResponse;
import com.goldstack.user.UserDtos.SignupRequest;
import com.goldstack.user.UserService;
import com.goldstack.wallet.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestContainers.class)
@ActiveProfiles("test")
class OrderConcurrencyTest {

    @Autowired
    UserService userService;
    @Autowired
    QuoteService quoteService;
    @Autowired
    OrderService orderService;
    @Autowired
    WalletService walletService;
    @Autowired
    GoldService goldService;

    @Test
    @DisplayName("Two simultaneous 6,000-coin buys on a 10,000 balance: exactly one succeeds")
    void twoSimultaneousBuysCannotOverspend() throws Exception {
        UUID userId = newUser();
        QuoteResponse q1 = buyQuote(userId, "6000");
        QuoteResponse q2 = buyQuote(userId, "6000");

        List<Outcome> outcomes = runAtSameTime(2, i -> () ->
                orderService.buy(userId, (i == 0 ? q1 : q2).quoteId(), "key-" + i + "-" + UUID.randomUUID()));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.error() instanceof InsufficientFundsException).hasSize(1);
        assertThat(walletService.balanceOf(userId)).isEqualByComparingTo("4000.00");
        assertThat(goldService.gramsOf(userId)).isEqualByComparingTo(q1.grams());
        assertLedgersBalanced();
    }

    @Test
    @DisplayName("Ten simultaneous 1,500-coin buys on 10,000: exactly six succeed, balance never negative")
    void manySimultaneousBuys() throws Exception {
        UUID userId = newUser();
        List<QuoteResponse> quotes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            quotes.add(buyQuote(userId, "1500"));
        }

        List<Outcome> outcomes = runAtSameTime(10, i -> () ->
                orderService.buy(userId, quotes.get(i).quoteId(), "many-" + i + "-" + UUID.randomUUID()));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(6);
        assertThat(walletService.balanceOf(userId)).isEqualByComparingTo("1000.00");
        assertLedgersBalanced();
    }

    @Test
    @DisplayName("Same Idempotency-Key sent 5 times at once: one purchase, five identical answers (Redis lock)")
    void sameIdempotencyKeyExecutesOnce() throws Exception {
        UUID userId = newUser();
        QuoteResponse quote = buyQuote(userId, "500");
        String key = "retry-" + UUID.randomUUID();

        List<Outcome> outcomes = runAtSameTime(5, i -> () -> orderService.buy(userId, quote.quoteId(), key));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(outcomes).extracting(o -> o.result().order().id()).containsOnly(outcomes.get(0).result().order().id());
        assertThat(outcomes).filteredOn(o -> !o.result().replayed()).hasSize(1);
        assertThat(walletService.balanceOf(userId)).isEqualByComparingTo("9500.00");
        assertLedgersBalanced();
    }

    private record Outcome(OrderResult result, Throwable error) {
        boolean succeeded() {
            return result != null;
        }
    }

    private List<Outcome> runAtSameTime(int threads, IntFunction<Callable<OrderResult>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<OrderResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<OrderResult> work = task.apply(i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return work.call();
            }));
        }
        ready.await();
        go.countDown();

        List<Outcome> outcomes = new ArrayList<>();
        for (Future<OrderResult> f : futures) {
            try {
                outcomes.add(new Outcome(f.get(), null));
            } catch (ExecutionException e) {
                outcomes.add(new Outcome(null, e.getCause()));
            }
        }
        pool.shutdown();
        return outcomes;
    }

    private UUID newUser() {
        String email = "user-" + UUID.randomUUID() + "@test.com";
        return userService.signup(new SignupRequest(email, "password123", "Test User")).user().id();
    }

    private QuoteResponse buyQuote(UUID userId, String coins) {
        return quoteService.createQuote(userId, new CreateQuoteRequest(Side.BUY, new BigDecimal(coins), null));
    }

    private void assertLedgersBalanced() {
        assertThat(walletService.reconcile().balanced()).as("coin ledger balanced").isTrue();
        assertThat(goldService.reconcile().balanced()).as("gold ledger balanced").isTrue();
    }
}
