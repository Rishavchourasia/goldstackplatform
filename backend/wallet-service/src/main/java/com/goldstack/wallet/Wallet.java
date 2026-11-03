package com.goldstack.wallet;

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
@Table(name = "wallets")
public class Wallet {

    public enum AccountType { USER, SYSTEM_BONUS, SYSTEM_TOPUP, SYSTEM_TREASURY }

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
    }

    static Wallet forUser(UUID userId, Instant now) {
        Wallet w = new Wallet();
        w.id = UUID.randomUUID();
        w.userId = userId;
        w.accountType = AccountType.USER;
        w.balance = new BigDecimal("0.00");
        w.updatedAt = now;
        return w;
    }

    void credit(BigDecimal amount, Instant now) {
        balance = balance.add(amount);
        updatedAt = now;
    }

    void debit(BigDecimal amount, Instant now) {
        if (accountType == AccountType.USER && balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("INSUFFICIENT_BALANCE",
                    "Wallet balance " + balance + " is less than required " + amount);
        }
        balance = balance.subtract(amount);
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
