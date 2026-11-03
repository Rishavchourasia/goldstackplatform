package com.goldstack.portfolio;

import com.goldstack.common.Frequency;
import com.goldstack.common.security.AuthUser;
import com.goldstack.common.web.PageResponse;
import com.goldstack.order.OrderDtos.OrderResponse;
import com.goldstack.portfolio.PortfolioService.HistoryPoint;
import com.goldstack.portfolio.PortfolioService.PortfolioSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@Tag(name = "Portfolio & what-if")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/api/portfolio")
    @Operation(summary = "Total grams, invested, current value, P&L, return %, coin balance")
    public PortfolioSummary summary(@AuthenticationPrincipal AuthUser user) {
        return portfolioService.summary(user.id());
    }

    @GetMapping("/api/portfolio/transactions")
    @Operation(summary = "Full transaction history (paginated, newest first)")
    public PageResponse<OrderResponse> transactions(@AuthenticationPrincipal AuthUser user,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return portfolioService.transactions(user.id(), page, size);
    }

    @GetMapping("/api/portfolio/history")
    @Operation(summary = "Daily value vs invested since the first order (for the portfolio chart)")
    public List<HistoryPoint> history(@AuthenticationPrincipal AuthUser user) {
        return portfolioService.history(user.id());
    }

    @GetMapping("/api/what-if/lump-sum")
    @SecurityRequirements
    @Operation(summary = "If I had invested 'amount' coins on 'date', what would it be worth today?")
    public WhatIfCalculator.LumpSumResult lumpSum(
            @RequestParam @DecimalMin("1.00") @DecimalMax("100000000") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return portfolioService.whatIfLumpSum(amount, date);
    }

    @GetMapping("/api/what-if/sip")
    @SecurityRequirements
    @Operation(summary = "If I had invested 'amount' every DAILY/WEEKLY/MONTHLY since 'startDate'... (with chart series)")
    public WhatIfCalculator.SipResult sip(
            @RequestParam @DecimalMin("1.00") @DecimalMax("10000000") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @RequestParam Frequency frequency,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        return portfolioService.whatIfSip(amount, frequency, startDate);
    }
}
