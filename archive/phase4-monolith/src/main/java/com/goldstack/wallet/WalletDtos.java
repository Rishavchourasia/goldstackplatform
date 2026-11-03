package com.goldstack.wallet;

import com.goldstack.common.LedgerDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class WalletDtos {

    private WalletDtos() {
    }

    public record WalletResponse(BigDecimal balance, Instant updatedAt) {
    }

    public record TopUpRequest(
            @NotNull @DecimalMin(value = "1.00") @Digits(integer = 15, fraction = 2) BigDecimal amount) {
    }

    public record LedgerEntryResponse(UUID id, UUID transactionId, LedgerDirection direction, BigDecimal amount,
                                      BigDecimal balanceAfter, CoinLedgerEntry.EntryType type, UUID referenceId,
                                      Instant createdAt) {
        static LedgerEntryResponse from(CoinLedgerEntry e) {
            return new LedgerEntryResponse(e.getId(), e.getTransactionId(), e.getDirection(), e.getAmount(),
                    e.getBalanceAfter(), e.getEntryType(), e.getReferenceId(), e.getCreatedAt());
        }
    }

    public record LedgerReport(BigDecimal sumOfAllBalances, long unbalancedTransactions, long walletsOutOfSync,
                               boolean balanced) {
    }
}
