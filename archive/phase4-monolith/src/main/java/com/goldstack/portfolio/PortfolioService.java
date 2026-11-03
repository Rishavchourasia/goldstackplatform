package com.goldstack.portfolio;

import com.goldstack.common.Frequency;
import com.goldstack.common.Money;
import com.goldstack.common.config.GoldStackProperties;
import com.goldstack.common.redis.RedisJson;
import com.goldstack.common.error.ApiException;
import com.goldstack.common.web.PageResponse;
import com.goldstack.order.OrderDtos.OrderResponse;
import com.goldstack.order.OrderService;
import com.goldstack.price.PriceDtos.LivePrice;
import com.goldstack.price.PriceService;
import com.goldstack.wallet.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final PortfolioPositionRepository positions;
    private final WalletService walletService;
    private final OrderService orderService;
    private final PriceService priceService;
    private final Clock clock;
    private final RedisJson redis;
    private final Duration whatIfTtl;

    public PortfolioService(PortfolioPositionRepository positions, WalletService walletService,
                            OrderService orderService, PriceService priceService, Clock clock,
                            RedisJson redis, GoldStackProperties props) {
        this.positions = positions;
        this.walletService = walletService;
        this.orderService = orderService;
        this.priceService = priceService;
        this.clock = clock;
        this.redis = redis;
        this.whatIfTtl = Duration.ofMinutes(props.whatIf().cacheTtlMinutes());
    }

    public record PortfolioSummary(BigDecimal coinBalance, BigDecimal totalGrams, BigDecimal totalInvested,
                                   BigDecimal currentPricePerGram, BigDecimal currentValue, BigDecimal profitLoss,
                                   BigDecimal returnPercent, Instant priceAt) {
    }

    public record HistoryPoint(LocalDate date, BigDecimal grams, BigDecimal invested, BigDecimal value) {
    }

    @Transactional(readOnly = true)
    public PortfolioSummary summary(UUID userId) {
        PortfolioPosition pos = positions.findById(userId).orElseThrow(() -> ApiException.notFound("Portfolio"));
        LivePrice price = priceService.currentPrice();
        BigDecimal value = Money.valueOf(pos.getTotalGrams(), price.pricePerGram());
        BigDecimal pnl = value.subtract(pos.getTotalInvested());
        return new PortfolioSummary(walletService.balanceOf(userId), pos.getTotalGrams(), pos.getTotalInvested(),
                price.pricePerGram(), value, pnl, Money.percent(pnl, pos.getTotalInvested()), price.recordedAt());
    }

    public PageResponse<OrderResponse> transactions(UUID userId, int page, int size) {
        return orderService.listOrders(userId, page, size);
    }

    public List<HistoryPoint> history(UUID userId) {
        List<OrderResponse> orders = orderService.allOrdersOldestFirst(userId);
        if (orders.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate start = LocalDate.ofInstant(orders.get(0).createdAt(), clock.getZone());
        NavigableMap<LocalDate, BigDecimal> closes = new TreeMap<>(priceService.dailyPriceMap(start, today));
        BigDecimal livePrice = priceService.currentPrice().pricePerGram();

        List<HistoryPoint> points = new ArrayList<>();
        AverageCost holding = AverageCost.empty();
        Iterator<OrderResponse> it = orders.iterator();
        OrderResponse next = it.next();
        BigDecimal lastPrice = livePrice;

        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {

            while (next != null && !LocalDate.ofInstant(next.createdAt(), clock.getZone()).isAfter(day)) {
                holding = holding.apply(next.side(), next.grams(), next.coins());
                next = it.hasNext() ? it.next() : null;
            }
            BigDecimal price = day.equals(today) ? livePrice
                    : closes.getOrDefault(day, lastPrice);
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

    private WhatIfCalculator.LumpSumResult computeLumpSum(BigDecimal amount, LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        validateStart(date, today);
        BigDecimal priceThen = date.equals(today) ? priceService.currentPrice().pricePerGram()
                : priceService.closeOn(date).orElseThrow(() -> noHistory(date));
        return WhatIfCalculator.lumpSum(amount, date, priceThen, priceService.currentPrice().pricePerGram());
    }

    private WhatIfCalculator.SipResult computeSip(BigDecimal amount, Frequency frequency, LocalDate startDate) {
        LocalDate today = LocalDate.now(clock);
        validateStart(startDate, today);

        NavigableMap<LocalDate, BigDecimal> closes = new TreeMap<>(priceService.dailyPriceMap(startDate.minusDays(7), today));
        if (closes.isEmpty()) {
            throw noHistory(startDate);
        }
        return WhatIfCalculator.sip(amount, frequency, startDate, today,
                d -> {
                    Map.Entry<LocalDate, BigDecimal> e = closes.floorEntry(d);
                    return e != null ? e.getValue() : closes.firstEntry().getValue();
                },
                priceService.currentPrice().pricePerGram());
    }

    private void validateStart(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            throw ApiException.badRequest("DATE_IN_FUTURE", "Date must be today or earlier");
        }
        LocalDate earliest = priceService.earliestDate().orElse(today);
        if (date.isBefore(earliest)) {
            throw ApiException.badRequest("DATE_TOO_EARLY", "Price history starts on " + earliest);
        }
    }

    private static ApiException noHistory(LocalDate date) {
        return ApiException.badRequest("NO_PRICE_HISTORY", "No price available for " + date);
    }
}
