package com.goldstack.wallet;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
class LedgerReconciler {

    private static final Logger log = LoggerFactory.getLogger(LedgerReconciler.class);

    private static final String UNBALANCED_TRANSACTIONS = """
            SELECT COUNT(*) FROM (
                SELECT transaction_id
                FROM coin_ledger_entries
                GROUP BY transaction_id
                HAVING COUNT(*) <> 2
                    OR SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END) <> 0
            ) broken
            """;

    private static final String MISMATCHED_WALLETS = """
            SELECT COUNT(*) FROM wallets w
            LEFT JOIN (
                SELECT wallet_id, SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END) AS net
                FROM coin_ledger_entries
                GROUP BY wallet_id
            ) l ON l.wallet_id = w.id
            WHERE w.balance <> COALESCE(l.net, 0)
            """;

    private static final String ALL_WALLETS_SUM = "SELECT COALESCE(SUM(balance), 0) FROM wallets";

    private final JdbcTemplate jdbc;
    private final AtomicLong lastImbalance = new AtomicLong();

    LedgerReconciler(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;

        Gauge.builder("goldstack.ledger.imbalance", lastImbalance, AtomicLong::get)
                .description("Problems found by the last ledger reconciliation run. Must be 0.")
                .register(registry);
    }

    @Scheduled(initialDelayString = "${goldstack.reconciliation.initial-delay:PT1M}",
               fixedDelayString = "${goldstack.reconciliation.every:PT5M}")
    @SchedulerLock(name = "ledgerReconciliation", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    public void runScheduled() {
        reconcile();
    }

    public Report reconcile() {
        Report report = new Report(
                count(UNBALANCED_TRANSACTIONS),
                count(MISMATCHED_WALLETS),
                jdbc.queryForObject(ALL_WALLETS_SUM, java.math.BigDecimal.class).signum() != 0 ? 1 : 0);

        lastImbalance.set(report.problems());
        if (report.problems() > 0) {
            log.error("LEDGER OUT OF BALANCE: {} unbalanced transactions, {} mismatched wallets, total-sum-check failed={}",
                    report.unbalancedTransactions(), report.mismatchedWallets(), report.totalSumWrong() == 1);
        } else {
            log.debug("Ledger reconciled: balanced");
        }
        return report;
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    record Report(long unbalancedTransactions, long mismatchedWallets, long totalSumWrong) {
        long problems() {
            return unbalancedTransactions + mismatchedWallets + totalSumWrong;
        }
    }
}
