package com.goldstack.price;

import com.goldstack.common.error.ApiException;
import com.goldstack.price.PriceDtos.DailyPriceResponse;
import com.goldstack.price.PriceDtos.LivePrice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/prices")
@Tag(name = "Gold prices (public)")
@SecurityRequirements
public class PriceController {

    private static final long MAX_RANGE_DAYS = 366L * 10;

    private final PriceService priceService;
    private final Clock clock;

    public PriceController(PriceService priceService, Clock clock) {
        this.priceService = priceService;
        this.clock = clock;
    }

    @GetMapping("/latest")
    @Operation(summary = "Current live price per gram (also pushed on ws://host/ws/price)")
    public LivePrice latest() {
        return priceService.currentPrice();
    }

    @GetMapping("/daily")
    @Operation(summary = "Daily closing prices in a date range (default: last 365 days)")
    public List<DailyPriceResponse> daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(365);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("INVALID_RANGE", "'from' must be on or before 'to'");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("RANGE_TOO_LARGE", "Range can be at most 10 years");
        }
        return priceService.dailyPrices(start, end);
    }

    @GetMapping("/ticks")
    @Operation(summary = "Most recent live ticks, newest first")
    public List<LivePrice> ticks(@RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return priceService.recentTicks(limit);
    }
}
