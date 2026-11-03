package com.goldstack.order;

import com.goldstack.common.LedgerDirection;
import com.goldstack.common.Side;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    @Schema(description = "BUY: send 'coins' (how much to spend). SELL: send 'grams' (how much to sell).")
    public record CreateQuoteRequest(
            @NotNull Side side,
            @DecimalMin("1.00") @Digits(integer = 15, fraction = 2) BigDecimal coins,
            @DecimalMin("0.0001") @Digits(integer = 10, fraction = 4) BigDecimal grams) {
    }

    public record QuoteResponse(UUID quoteId, Side side, BigDecimal pricePerGram, BigDecimal coins, BigDecimal grams,
                                Instant expiresAt) {
        static QuoteResponse from(Quote q) {
            return new QuoteResponse(q.quoteId(), q.side(), q.pricePerGram(), q.coins(), q.grams(), q.expiresAt());
        }
    }

    public record ExecuteOrderRequest(@NotNull UUID quoteId) {
    }

    public record OrderResponse(UUID id, Side side, String source, BigDecimal coins, BigDecimal grams,
                                BigDecimal pricePerGram, String status, String failureCode, String failureReason,
                                Instant createdAt) {
        static OrderResponse from(GoldOrder o) {
            return new OrderResponse(o.getId(), o.getSide(), o.getSource().name(), o.getCoins(), o.getGrams(),
                    o.getPricePerGram(), o.getStatus().name(), o.getFailureCode(), o.getFailureReason(),
                    o.getCreatedAt());
        }
    }

    public record OrderResult(OrderResponse order, boolean replayed) {
    }

    public record GoldBalanceResponse(BigDecimal grams) {
    }

    public record GoldLedgerEntryResponse(UUID id, UUID transactionId, LedgerDirection direction, BigDecimal grams,
                                          BigDecimal balanceAfter, String type, UUID referenceId, Instant createdAt) {
        static GoldLedgerEntryResponse from(GoldLedgerEntry e) {
            return new GoldLedgerEntryResponse(e.getId(), e.getTransactionId(), e.getDirection(), e.getGrams(),
                    e.getBalanceAfter(), e.getEntryType().name(), e.getReferenceId(), e.getCreatedAt());
        }
    }

    public record GoldLedgerReport(BigDecimal sumOfAllBalances, long unbalancedTransactions, long accountsOutOfSync,
                                   boolean balanced) {
    }
}
