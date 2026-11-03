package com.goldstack.order;

import com.goldstack.common.LedgerDirection;
import com.goldstack.common.error.InsufficientFundsException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gold_accounts")
class GoldAccount {

    enum AccountType { USER, SYSTEM_VAULT }

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal grams;

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GoldAccount() {
    }

    static GoldAccount forUser(UUID userId, Instant now) {
        GoldAccount a = new GoldAccount();
        a.id = UUID.randomUUID();
        a.userId = userId;
        a.accountType = AccountType.USER;
        a.grams = new BigDecimal("0.0000");
        a.updatedAt = now;
        return a;
    }

    void credit(BigDecimal amount, Instant now) {
        grams = grams.add(amount);
        updatedAt = now;
    }

    void debit(BigDecimal amount, Instant now) {
        if (accountType == AccountType.USER && grams.compareTo(amount) < 0) {
            throw new InsufficientFundsException("INSUFFICIENT_GOLD",
                    "Gold balance " + grams + " g is less than " + amount + " g");
        }
        grams = grams.subtract(amount);
        updatedAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    BigDecimal getGrams() {
        return grams;
    }
}

@Entity
@Table(name = "gold_ledger_entries")
class GoldLedgerEntry {

    enum EntryType { BUY, SELL }

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "gold_account_id", nullable = false)
    private UUID goldAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerDirection direction;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal grams;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GoldLedgerEntry() {
    }

    GoldLedgerEntry(UUID transactionId, UUID goldAccountId, LedgerDirection direction, BigDecimal grams,
                    BigDecimal balanceAfter, EntryType entryType, UUID referenceId, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.goldAccountId = goldAccountId;
        this.direction = direction;
        this.grams = grams;
        this.balanceAfter = balanceAfter;
        this.entryType = entryType;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getTransactionId() {
        return transactionId;
    }

    LedgerDirection getDirection() {
        return direction;
    }

    BigDecimal getGrams() {
        return grams;
    }

    BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    EntryType getEntryType() {
        return entryType;
    }

    UUID getReferenceId() {
        return referenceId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
