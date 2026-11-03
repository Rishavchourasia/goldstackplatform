package com.goldstack.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    @Query("select w.id from Wallet w where w.userId = :userId")
    Optional<UUID> findIdByUserId(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id in :ids order by w.id")
    List<Wallet> lockAllInIdOrder(@Param("ids") Collection<UUID> ids);

    @Query("select coalesce(sum(w.balance), 0) from Wallet w")
    BigDecimal sumOfAllBalances();
}

interface CoinLedgerEntryRepository extends JpaRepository<CoinLedgerEntry, UUID> {

    Page<CoinLedgerEntry> findByWalletId(UUID walletId, Pageable pageable);

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT transaction_id FROM coin_ledger_entries
                GROUP BY transaction_id
                HAVING SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END) <> 0
            ) unbalanced
            """, nativeQuery = true)
    long countUnbalancedTransactions();

    @Query(value = """
            SELECT COUNT(*) FROM wallets w
            WHERE w.balance <> COALESCE((
                SELECT SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END)
                FROM coin_ledger_entries e WHERE e.wallet_id = w.id), 0)
            """, nativeQuery = true)
    long countWalletsOutOfSync();
}
