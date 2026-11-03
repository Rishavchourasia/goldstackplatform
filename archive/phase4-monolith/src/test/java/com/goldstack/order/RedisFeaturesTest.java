package com.goldstack.order;

import com.goldstack.TestContainers;
import com.goldstack.common.error.ApiException;
import com.goldstack.common.error.InsufficientFundsException;
import com.goldstack.order.OrderDtos.CreateQuoteRequest;
import com.goldstack.order.OrderDtos.OrderResult;
import com.goldstack.order.OrderDtos.QuoteResponse;
import com.goldstack.portfolio.PortfolioService;
import com.goldstack.price.PriceService;
import com.goldstack.user.UserDtos.SignupRequest;
import com.goldstack.user.UserService;
import com.goldstack.wallet.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestContainers.class)
@ActiveProfiles("test")
class RedisFeaturesTest {

    @Autowired StringRedisTemplate redis;
    @Autowired UserService userService;
    @Autowired QuoteService quoteService;
    @Autowired OrderService orderService;
    @Autowired WalletService walletService;
    @Autowired PriceService priceService;
    @Autowired PortfolioService portfolioService;

    @Test
    @DisplayName("gold:price:latest holds the newest tick (no TTL)")
    void latestPriceIsCachedInRedis() {
        priceService.recordTick(new BigDecimal("12345.67"), "TEST");

        assertThat(redis.opsForValue().get("gold:price:latest")).contains("\"pricePerGram\":\"12345.67\"");
        assertThat(redis.getExpire("gold:price:latest")).isEqualTo(-1L);
        assertThat(priceService.currentPrice().pricePerGram()).isEqualByComparingTo("12345.67");
    }

    @Test
    @DisplayName("quote:{id} expires after 5 minutes and can be used only once")
    void quoteLivesInRedisWithTtlAndIsSingleUse() {
        UUID user = newUser();
        QuoteResponse q = buyQuote(user, "500");
        String key = "quote:" + q.quoteId();

        assertThat(redis.getExpire(key)).isBetween(290L, 300L);

        orderService.buy(user, q.quoteId(), "first-" + UUID.randomUUID());
        assertThat(redis.hasKey(key)).isFalse();

        assertThatThrownBy(() -> orderService.buy(user, q.quoteId(), "second-" + UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(410));
    }

    @Test
    @DisplayName("A failed buy puts the quote back, and the same key can be retried after a top-up")
    void failedBuyRestoresQuoteAndReleasesKey() {
        UUID user = newUser();
        QuoteResponse big = buyQuote(user, "6000");
        QuoteResponse big2 = buyQuote(user, "6000");
        orderService.buy(user, big.quoteId(), "a-" + UUID.randomUUID());

        String key = "retry-" + UUID.randomUUID();
        assertThatThrownBy(() -> orderService.buy(user, big2.quoteId(), key))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(redis.hasKey("quote:" + big2.quoteId())).as("quote put back").isTrue();
        assertThat(redis.hasKey(IdempotencyStore.key(user, key))).as("key released").isFalse();

        walletService.topUp(user, new BigDecimal("2000"));
        OrderResult ok = orderService.buy(user, big2.quoteId(), key);
        assertThat(ok.replayed()).isFalse();
        assertThat(walletService.balanceOf(user)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("idem:{user}:{key} stores the result for 24 h; if Redis loses it, Postgres still prevents a repeat")
    void idempotencyResultCachedAndBackedByDatabase() {
        UUID user = newUser();
        QuoteResponse q = buyQuote(user, "500");
        String key = "idem-" + UUID.randomUUID();
        OrderResult first = orderService.buy(user, q.quoteId(), key);

        String redisKey = IdempotencyStore.key(user, key);
        assertThat(redis.opsForValue().get(redisKey)).contains("COMPLETED").contains(first.order().id().toString());
        assertThat(redis.getExpire(redisKey)).isBetween(86_000L, 86_400L);

        OrderResult fromRedis = orderService.buy(user, q.quoteId(), key);
        assertThat(fromRedis.replayed()).isTrue();
        assertThat(fromRedis.order().id()).isEqualTo(first.order().id());

        redis.delete(redisKey);
        OrderResult fromDb = orderService.buy(user, q.quoteId(), key);
        assertThat(fromDb.replayed()).isTrue();
        assertThat(fromDb.order().id()).isEqualTo(first.order().id());
        assertThat(walletService.balanceOf(user)).isEqualByComparingTo("9500.00");
    }

    @Test
    @DisplayName("whatif:{amount}:{date} caches the calculator result for 1 hour")
    void whatIfResultIsCached() {
        LocalDate date = LocalDate.now().minusMonths(3);
        var first = portfolioService.whatIfLumpSum(new BigDecimal("10000"), date);

        String key = "whatif:10000.00:" + date;
        assertThat(redis.hasKey(key)).isTrue();
        assertThat(redis.getExpire(key)).isBetween(3590L, 3600L);

        var second = portfolioService.whatIfLumpSum(new BigDecimal("10000.00"), date);
        assertThat(second).isEqualTo(first);
    }

    private UUID newUser() {
        return userService.signup(new SignupRequest("r-" + UUID.randomUUID() + "@test.com", "password123", "Redis Test"))
                .user().id();
    }

    private QuoteResponse buyQuote(UUID user, String coins) {
        return quoteService.createQuote(user, new CreateQuoteRequest(Side.BUY, new BigDecimal(coins), null));
    }
}
