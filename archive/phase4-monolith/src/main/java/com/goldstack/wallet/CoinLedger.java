package com.goldstack.wallet;

import com.goldstack.common.LedgerDirection;
import com.goldstack.wallet.CoinLedgerEntry.EntryType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
class CoinLedger {

    private final WalletRepository wallets;
    private final CoinLedgerEntryRepository entries;
    private final Clock clock;

    CoinLedger(WalletRepository wallets, CoinLedgerEntryRepository entries, Clock clock) {
        this.wallets = wallets;
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    UUID transfer(UUID fromWalletId, UUID toWalletId, BigDecimal amount, EntryType type, UUID referenceId) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive: " + amount);
        }
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }

        Map<UUID, Wallet> locked = wallets.lockAllInIdOrder(List.of(fromWalletId, toWalletId)).stream()
                .collect(Collectors.toMap(Wallet::getId, Function.identity()));
        Wallet from = locked.get(fromWalletId);
        Wallet to = locked.get(toWalletId);
        if (from == null || to == null) {
            throw new IllegalStateException("Wallet missing for transfer " + fromWalletId + " -> " + toWalletId);
        }

        Instant now = Instant.now(clock);
        from.debit(amount, now);
        to.credit(amount, now);

        UUID transactionId = UUID.randomUUID();
        entries.save(new CoinLedgerEntry(transactionId, from.getId(), LedgerDirection.DEBIT, amount,
                from.getBalance(), type, referenceId, now));
        entries.save(new CoinLedgerEntry(transactionId, to.getId(), LedgerDirection.CREDIT, amount,
                to.getBalance(), type, referenceId, now));

        return transactionId;
    }
}
