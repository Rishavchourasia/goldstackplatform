package com.goldstack.order;

import com.goldstack.common.LedgerDirection;
import com.goldstack.common.error.ApiException;
import com.goldstack.common.web.PageResponse;
import com.goldstack.common.web.Paging;
import com.goldstack.order.OrderDtos.GoldBalanceResponse;
import com.goldstack.order.OrderDtos.GoldLedgerEntryResponse;
import com.goldstack.order.OrderDtos.GoldLedgerReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GoldService {

    static final UUID SYSTEM_VAULT = UUID.fromString("00000000-0000-0000-0000-000000000101");

    private final GoldAccountRepository accounts;
    private final GoldLedgerEntryRepository entries;
    private final Clock clock;

    private final AtomicBoolean failNextGoldCredit = new AtomicBoolean(false);

    public GoldService(GoldAccountRepository accounts, GoldLedgerEntryRepository entries, Clock clock) {
        this.accounts = accounts;
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional
    public GoldBalanceResponse balance(UUID userId) {
        return new GoldBalanceResponse(gramsOf(userId));
    }

    @Transactional
    public BigDecimal gramsOf(UUID userId) {
        UUID id = ensureAccount(userId);
        return accounts.findById(id).map(GoldAccount::getGrams).orElseThrow(() -> ApiException.notFound("Gold account"));
    }

    @Transactional(readOnly = true)
    public PageResponse<GoldLedgerEntryResponse> ledger(UUID userId, int page, int size) {
        UUID id = accounts.findIdByUserId(userId).orElse(null);
        if (id == null) {
            return new PageResponse<>(List.of(), page, size, 0, 0);
        }
        return PageResponse.of(entries.findByGoldAccountId(id, Paging.newestFirst(page, size, "createdAt")),
                GoldLedgerEntryResponse::from);
    }

    @Transactional(readOnly = true)
    public GoldLedgerReport reconcile() {
        BigDecimal sum = accounts.sumOfAllBalances();
        long unbalanced = entries.countUnbalancedTransactions();
        long outOfSync = entries.countAccountsOutOfSync();
        return new GoldLedgerReport(sum, unbalanced, outOfSync, sum.signum() == 0 && unbalanced == 0 && outOfSync == 0);
    }

    void failNextGoldCredit() {
        failNextGoldCredit.set(true);
    }

    boolean shouldFailThisGoldCredit() {
        return failNextGoldCredit.getAndSet(false);
    }

    void deliverGold(UUID userId, BigDecimal grams, UUID orderId) {
        transfer(SYSTEM_VAULT, ensureAccount(userId), grams, GoldLedgerEntry.EntryType.BUY, orderId);
    }

    void takeBackGold(UUID userId, BigDecimal grams, UUID orderId) {
        transfer(ensureAccount(userId), SYSTEM_VAULT, grams, GoldLedgerEntry.EntryType.SELL, orderId);
    }

    void returnGold(UUID userId, BigDecimal grams, UUID orderId) {
        transfer(SYSTEM_VAULT, ensureAccount(userId), grams, GoldLedgerEntry.EntryType.SELL_REVERSAL, orderId);
    }

    private UUID ensureAccount(UUID userId) {
        accounts.insertIfMissing(UUID.randomUUID(), userId, Instant.now(clock));
        return accounts.findIdByUserId(userId).orElseThrow(() -> ApiException.notFound("Gold account"));
    }

    private void transfer(UUID fromId, UUID toId, BigDecimal grams, GoldLedgerEntry.EntryType type, UUID ref) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Gold transfers must run inside a transaction");
        }
        Map<UUID, GoldAccount> locked = accounts.lockAllInIdOrder(List.of(fromId, toId)).stream()
                .collect(Collectors.toMap(GoldAccount::getId, Function.identity()));
        GoldAccount from = locked.get(fromId);
        GoldAccount to = locked.get(toId);
        if (from == null || to == null) {
            throw new IllegalStateException("Gold account missing for transfer " + fromId + " -> " + toId);
        }
        Instant now = Instant.now(clock);
        from.debit(grams, now);
        to.credit(grams, now);

        UUID txId = UUID.randomUUID();
        entries.save(new GoldLedgerEntry(txId, from.getId(), LedgerDirection.DEBIT, grams, from.getGrams(), type, ref, now));
        entries.save(new GoldLedgerEntry(txId, to.getId(), LedgerDirection.CREDIT, grams, to.getGrams(), type, ref, now));
    }
}
