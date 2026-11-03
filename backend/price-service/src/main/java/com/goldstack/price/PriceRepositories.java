package com.goldstack.price;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface PriceTickRepository extends JpaRepository<PriceTick, Long> {

    Optional<PriceTick> findTopByOrderByRecordedAtDesc();

    List<PriceTick> findByOrderByRecordedAtDesc(Pageable pageable);
}

interface DailyPriceRepository extends JpaRepository<DailyPrice, LocalDate> {

    Optional<DailyPrice> findTopByPriceDateLessThanEqualOrderByPriceDateDesc(LocalDate date);

    List<DailyPrice> findByPriceDateBetweenOrderByPriceDateAsc(LocalDate from, LocalDate to);

    Optional<DailyPrice> findTopByOrderByPriceDateAsc();

    Optional<DailyPrice> findTopByOrderByPriceDateDesc();

    @Modifying
    @Query(value = """
            INSERT INTO daily_prices (price_date, close_price) VALUES (:date, :price)
            ON CONFLICT (price_date) DO UPDATE SET close_price = EXCLUDED.close_price
            """, nativeQuery = true)
    void upsert(@Param("date") LocalDate date, @Param("price") BigDecimal price);
}
