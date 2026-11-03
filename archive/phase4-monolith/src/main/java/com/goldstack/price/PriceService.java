package com.goldstack.price;

import com.goldstack.common.Money;
import com.goldstack.common.config.GoldStackProperties;
import com.goldstack.common.redis.RedisJson;
import com.goldstack.price.PriceDtos.DailyPriceResponse;
import com.goldstack.price.PriceDtos.LivePrice;
import com.goldstack.price.PriceDtos.PriceUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PriceService {

    static final String LATEST_KEY = "gold:price:latest";
    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    private final PriceTickRepository ticks;
    private final DailyPriceRepository daily;
    private final ApplicationEventPublisher events;
    private final GoldStackProperties props;
    private final Clock clock;
    private final RedisJson redis;

    public PriceService(PriceTickRepository ticks, DailyPriceRepository daily, ApplicationEventPublisher events,
                        GoldStackProperties props, Clock clock, RedisJson redis) {
        this.ticks = ticks;
        this.daily = daily;
        this.events = events;
        this.props = props;
        this.clock = clock;
        this.redis = redis;
    }

    public LivePrice currentPrice() {
        try {
            Optional<LivePrice> cached = redis.get(LATEST_KEY, LivePrice.class);
            if (cached.isPresent()) {
                return cached.get();
            }
            LivePrice loaded = loadFromDatabase();
            redis.set(LATEST_KEY, loaded, null);
            return loaded;
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, reading latest price from Postgres: {}", e.getMessage());
            return loadFromDatabase();
        }
    }

    private LivePrice loadFromDatabase() {
        return ticks.findTopByOrderByRecordedAtDesc()
                .map(t -> new LivePrice(t.getPricePerGram(), t.getRecordedAt(), t.getSource()))
                .or(() -> daily.findTopByOrderByPriceDateDesc()
                        .map(d -> new LivePrice(d.getClosePrice(), Instant.now(clock), "DAILY_CLOSE")))
                .orElseGet(() -> new LivePrice(Money.price(props.price().seedEndPrice()), Instant.now(clock), "DEFAULT"));
    }

    @Transactional
    public LivePrice recordTick(BigDecimal pricePerGram, String source) {
        Instant now = Instant.now(clock);
        BigDecimal price = Money.price(pricePerGram);
        ticks.save(new PriceTick(price, source, now));
        daily.upsert(LocalDate.now(clock), price);

        LivePrice live = new LivePrice(price, now, source);
        try {
            redis.set(LATEST_KEY, live, null);
        } catch (DataAccessException e) {
            log.warn("Could not update {} in Redis: {}", LATEST_KEY, e.getMessage());
        }
        events.publishEvent(new PriceUpdatedEvent(live));
        return live;
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> closeOn(LocalDate date) {
        return daily.findTopByPriceDateLessThanEqualOrderByPriceDateDesc(date).map(DailyPrice::getClosePrice);
    }

    @Transactional(readOnly = true)
    public List<DailyPriceResponse> dailyPrices(LocalDate from, LocalDate to) {
        return daily.findByPriceDateBetweenOrderByPriceDateAsc(from, to).stream()
                .map(d -> new DailyPriceResponse(d.getPriceDate(), d.getClosePrice()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, BigDecimal> dailyPriceMap(LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
        daily.findByPriceDateBetweenOrderByPriceDateAsc(from, to)
                .forEach(d -> map.put(d.getPriceDate(), d.getClosePrice()));
        return map;
    }

    @Transactional(readOnly = true)
    public Optional<LocalDate> earliestDate() {
        return daily.findTopByOrderByPriceDateAsc().map(DailyPrice::getPriceDate);
    }

    @Transactional(readOnly = true)
    public List<LivePrice> recentTicks(int limit) {
        return ticks.findByOrderByRecordedAtDesc(PageRequest.of(0, limit)).stream()
                .map(t -> new LivePrice(t.getPricePerGram(), t.getRecordedAt(), t.getSource()))
                .toList();
    }
}
