package com.goldstack.price;

import com.goldstack.common.config.GoldStackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
class HistoricalPriceSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HistoricalPriceSeeder.class);

    private final DailyPriceRepository daily;
    private final JdbcTemplate jdbc;
    private final GoldStackProperties props;
    private final Clock clock;

    HistoricalPriceSeeder(DailyPriceRepository daily, JdbcTemplate jdbc, GoldStackProperties props, Clock clock) {
        this.daily = daily;
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (daily.count() > 0) {
            return;
        }
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        LocalDate from = yesterday.minusYears(props.price().seedYears());
        Map<LocalDate, BigDecimal> prices = HistoricalPriceGenerator.generate(from, yesterday, props.price().seedEndPrice());

        List<Object[]> rows = new ArrayList<>(prices.size());
        prices.forEach((date, price) -> rows.add(new Object[]{Date.valueOf(date), price}));
        jdbc.batchUpdate("INSERT INTO daily_prices (price_date, close_price) VALUES (?, ?) ON CONFLICT DO NOTHING", rows);

        log.info("Seeded {} days of historical gold prices ({} to {})", rows.size(), from, yesterday);
    }
}
