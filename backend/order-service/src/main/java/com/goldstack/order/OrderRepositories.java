package com.goldstack.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface GoldOrderRepository extends JpaRepository<GoldOrder, UUID> {

    Optional<GoldOrder> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    boolean existsByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Page<GoldOrder> findByUserId(UUID userId, Pageable pageable);

    Optional<GoldOrder> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from GoldOrder o where o.id = :id")
    Optional<GoldOrder> lockById(@Param("id") UUID id);
}

interface GoldAccountRepository extends JpaRepository<GoldAccount, UUID> {

    Optional<GoldAccount> findByUserId(UUID userId);

    @Query("select a.id from GoldAccount a where a.userId = :userId")
    Optional<UUID> findIdByUserId(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from GoldAccount a where a.id in :ids order by a.id")
    List<GoldAccount> lockAllInIdOrder(@Param("ids") Collection<UUID> ids);

    @Query("select coalesce(sum(a.grams), 0) from GoldAccount a")
    BigDecimal sumOfAllBalances();

    @Modifying
    @Query(value = """
            INSERT INTO gold_accounts (id, user_id, account_type, grams, version, updated_at)
            VALUES (:id, :userId, 'USER', 0, 0, :now) ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfMissing(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);
}

interface GoldLedgerEntryRepository extends JpaRepository<GoldLedgerEntry, UUID> {

    Page<GoldLedgerEntry> findByGoldAccountId(UUID goldAccountId, Pageable pageable);

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT transaction_id FROM gold_ledger_entries
                GROUP BY transaction_id
                HAVING SUM(CASE WHEN direction = 'CREDIT' THEN grams ELSE -grams END) <> 0
            ) unbalanced
            """, nativeQuery = true)
    long countUnbalancedTransactions();

    @Query(value = """
            SELECT COUNT(*) FROM gold_accounts a
            WHERE a.grams <> COALESCE((
                SELECT SUM(CASE WHEN e.direction = 'CREDIT' THEN e.grams ELSE -e.grams END)
                FROM gold_ledger_entries e WHERE e.gold_account_id = a.id), 0)
            """, nativeQuery = true)
    long countAccountsOutOfSync();
}
