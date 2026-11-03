package com.goldstack.wallet;

import com.goldstack.common.LedgerDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coin_ledger_entries")
public class CoinLedgerEntry {

    public enum EntryType { SIGNUP_BONUS, TOPUP, BUY, SELL, REFUND }

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerDirection direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CoinLedgerEntry() {
    }

    CoinLedgerEntry(UUID transactionId, UUID walletId, LedgerDirection direction, BigDecimal amount,
                    BigDecimal balanceAfter, EntryType entryType, UUID referenceId, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.walletId = walletId;
        this.direction = direction;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.entryType = entryType;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public LedgerDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
