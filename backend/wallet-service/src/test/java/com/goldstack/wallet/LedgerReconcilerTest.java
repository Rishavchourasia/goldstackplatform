package com.goldstack.wallet;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(LedgerReconcilerTest.Containers.class)
@ActiveProfiles("test")
class LedgerReconcilerTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    private static final UUID SYSTEM_BONUS = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SYSTEM_TREASURY = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired LedgerReconciler reconciler;
    @Autowired CoinLedger ledger;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry registry;

    @Test
    @DisplayName("A ledger built only through CoinLedger reconciles to zero problems")
    void healthyLedgerBalances() {
        UUID user = createUserWallet();
        tx.executeWithoutResult(s -> {
            ledger.transfer(SYSTEM_BONUS, user, new BigDecimal("1000.00"), CoinLedgerEntry.EntryType.SIGNUP_BONUS, null);
            ledger.transfer(user, SYSTEM_TREASURY, new BigDecimal("250.00"), CoinLedgerEntry.EntryType.BUY, null);
        });

        LedgerReconciler.Report report = reconciler.reconcile();

        assertThat(report.problems()).isZero();
        assertThat(gauge()).isZero();
    }

    @Test
    @DisplayName("A wallet balance changed outside the ledger is caught and raises the gauge")
    void tamperedBalanceIsDetected() {
        UUID user = createUserWallet();
        tx.executeWithoutResult(s ->
                ledger.transfer(SYSTEM_BONUS, user, new BigDecimal("100.00"), CoinLedgerEntry.EntryType.SIGNUP_BONUS, null));

        jdbc.update("UPDATE wallets SET balance = balance + 5 WHERE id = ?", user);
        try {
            LedgerReconciler.Report report = reconciler.reconcile();

            assertThat(report.mismatchedWallets()).isEqualTo(1);
            assertThat(report.problems()).isPositive();
            assertThat(gauge()).isPositive();
        } finally {

            jdbc.update("UPDATE wallets SET balance = balance - 5 WHERE id = ?", user);
        }
    }

    private UUID createUserWallet() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO wallets (id, user_id, account_type, balance, version, updated_at) VALUES (?, ?, 'USER', 0, 0, ?)",
                id, UUID.randomUUID(), java.sql.Timestamp.from(Instant.now()));
        return id;
    }

    private double gauge() {
        return registry.get("goldstack.ledger.imbalance").gauge().value();
    }
}
