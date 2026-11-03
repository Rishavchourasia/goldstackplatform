package com.goldstack.portfolio;

import com.goldstack.common.Frequency;
import com.goldstack.common.Money;
import com.goldstack.common.Side;
import com.goldstack.common.error.ApiException;
import com.goldstack.common.redis.RedisJson;
import com.goldstack.common.web.PageResponse;
import com.goldstack.common.web.Paging;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    @Validated
    @ConfigurationProperties(prefix = "goldstack.what-if")
    public record WhatIfProperties(@Positive long cacheTtlMinutes) {
    }

    public record PortfolioSummary(BigDecimal coinBalance, BigDecimal totalGrams, BigDecimal totalInvested,
                                   BigDecimal currentPricePerGram, BigDecimal currentValue, BigDecimal profitLoss,
                                   BigDecimal returnPercent, Instant priceAt) {
    }

    public record HistoryPoint(LocalDate date, BigDecimal grams, BigDecimal invested, BigDecimal value) {
    }

    public record TransactionResponse(UUID id, Side side, String source, BigDecimal coins, BigDecimal grams,
                                      BigDecimal pricePerGram, String status, Instant createdAt) {
        static TransactionResponse from(PortfolioTransaction t) {
            return new TransactionResponse(t.getOrderId(), t.getSide(), t.getSource(), t.getCoins(), t.getGrams(),
                    t.getPricePerGram(), "COMPLETED", t.getCompletedAt());
        }
    }

    private final PortfolioPositionRepository positions;
    private final PortfolioTransactionRepository transactions;
    private final LatestPrice latestPrice;
    private final Clients.PriceClient priceClient;
    private final Clients.WalletClient walletClient;
    private final RedisJson redis;
    private final Duration whatIfTtl;
    private final Clock clock;

    public PortfolioService(PortfolioPositionRepository positions, PortfolioTransactionRepository transactions,
                            LatestPrice latestPrice, Clients.PriceClient priceClient, Clients.WalletClient walletClient,
                            RedisJson redis, WhatIfProperties props, Clock clock) {
        this.positions = positions;
        this.transactions = transactions;
        this.latestPrice = latestPrice;
        this.priceClient = priceClient;
        this.walletClient = walletClient;
        this.redis = redis;
        this.whatIfTtl = Duration.ofMinutes(props.cacheTtlMinutes());
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PortfolioSummary summary(UUID userId) {
        BigDecimal grams = new BigDecimal("0.0000");
        BigDecimal invested = new BigDecimal("0.00");
        Optional<PortfolioPosition> pos = positions.findById(userId);
        if (pos.isPresent()) {
            grams = pos.get().getTotalGrams();
            invested = pos.get().getTotalInvested();
        }
        LatestPrice.Value price = latestPrice.get();
        BigDecimal value = Money.valueOf(grams, price.pricePerGram());
        BigDecimal pnl = value.subtract(invested);
        return new PortfolioSummary(walletClient.balance(userId).balance(), grams, invested, price.pricePerGram(),
                value, pnl, Money.percent(pnl, invested), price.at());
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> transactions(UUID userId, int page, int size) {
        return PageResponse.of(transactions.findByUserId(userId, Paging.newestFirst(page, size, "completedAt")),
                TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public List<HistoryPoint> history(UUID userId) {
        List<PortfolioTransaction> txs = transactions.findByUserIdOrderByCompletedAtAsc(userId);
        if (txs.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate start = LocalDate.ofInstant(txs.get(0).getCompletedAt(), clock.getZone());
        NavigableMap<LocalDate, BigDecimal> closes = dailyCloses(start, today);
        BigDecimal livePrice = latestPrice.get().pricePerGram();

        List<HistoryPoint> points = new ArrayList<>();
        AverageCost holding = AverageCost.empty();
        Iterator<PortfolioTransaction> it = txs.iterator();
        PortfolioTransaction next = it.next();
        BigDecimal lastPrice = livePrice;
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            while (next != null && !LocalDate.ofInstant(next.getCompletedAt(), clock.getZone()).isAfter(day)) {
                holding = holding.apply(next.getSide(), next.getGrams(), next.getCoins());
                next = it.hasNext() ? it.next() : null;
            }
            BigDecimal price = day.equals(today) ? livePrice : closes.getOrDefault(day, lastPrice);
            lastPrice = price;
            points.add(new HistoryPoint(day, holding.grams(), holding.invested(), Money.valueOf(holding.grams(), price)));
        }
        return points;
    }

    public WhatIfCalculator.LumpSumResult whatIfLumpSum(BigDecimal amount, LocalDate date) {
        String key = "whatif:" + Money.coins(amount) + ":" + date;
        return cached(key, WhatIfCalculator.LumpSumResult.class, () -> computeLumpSum(amount, date));
    }

    public WhatIfCalculator.SipResult whatIfSip(BigDecimal amount, Frequency frequency, LocalDate startDate) {
        String key = "whatif:sip:" + Money.coins(amount) + ":" + frequency + ":" + startDate;
        return cached(key, WhatIfCalculator.SipResult.class, () -> computeSip(amount, frequency, startDate));
    }

    private WhatIfCalculator.LumpSumResult computeLumpSum(BigDecimal amount, LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        validateStart(date, today);
        BigDecimal priceNow = latestPrice.get().pricePerGram();
        BigDecimal priceThen = date.equals(today) ? priceNow
                : Optional.ofNullable(dailyCloses(date.minusDays(7), date).floorEntry(date))
                .map(Map.Entry::getValue).orElseThrow(() -> noHistory(date));
        return WhatIfCalculator.lumpSum(amount, date, priceThen, priceNow);
    }

    private WhatIfCalculator.SipResult computeSip(BigDecimal amount, Frequency frequency, LocalDate startDate) {
        LocalDate today = LocalDate.now(clock);
        validateStart(startDate, today);
        NavigableMap<LocalDate, BigDecimal> closes = dailyCloses(startDate.minusDays(7), today);
        if (closes.isEmpty()) {
            throw noHistory(startDate);
        }
        return WhatIfCalculator.sip(amount, frequency, startDate, today,
                d -> {
                    Map.Entry<LocalDate, BigDecimal> e = closes.floorEntry(d);
                    return e != null ? e.getValue() : closes.firstEntry().getValue();
                },
                latestPrice.get().pricePerGram());
    }

    private NavigableMap<LocalDate, BigDecimal> dailyCloses(LocalDate from, LocalDate to) {
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        priceClient.daily(from, to).forEach(d -> map.put(d.date(), d.closePrice()));
        return map;
    }

    private void validateStart(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            throw ApiException.badRequest("DATE_IN_FUTURE", "Date must be today or earlier");
        }
        LocalDate earliest = Optional.ofNullable(priceClient.earliest().date()).orElse(today);
        if (date.isBefore(earliest)) {
            throw ApiException.badRequest("DATE_TOO_EARLY", "Price history starts on " + earliest);
        }
    }

    private <T> T cached(String key, Class<T> type, Supplier<T> compute) {
        try {
            Optional<T> hit = redis.get(key, type);
            if (hit.isPresent()) {
                return hit.get();
            }
        } catch (DataAccessException e) {
            log.warn("What-if cache unavailable, computing directly: {}", e.getMessage());
            return compute.get();
        }
        T value = compute.get();
        try {
            redis.set(key, value, whatIfTtl);
        } catch (DataAccessException e) {
            log.warn("Could not cache {}: {}", key, e.getMessage());
        }
        return value;
    }

    private static ApiException noHistory(LocalDate date) {
        return ApiException.badRequest("NO_PRICE_HISTORY", "No price available for " + date);
    }
}
