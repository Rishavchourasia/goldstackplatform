package com.goldstack.order;

import com.goldstack.common.LedgerDirection;
import com.goldstack.common.error.ApiException;
import com.goldstack.common.web.PageResponse;
import com.goldstack.common.web.Paging;
import com.goldstack.order.OrderDtos.GoldBalanceResponse;
import com.goldstack.order.OrderDtos.GoldLedgerEntryResponse;
import com.goldstack.order.OrderDtos.GoldLedgerReport;
import org.springframework.stereotype.Service;
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

@Service
public class GoldService {

    static final UUID SYSTEM_VAULT = UUID.fromString("00000000-0000-0000-0000-000000000101");

    private final GoldAccountRepository accounts;
    private final GoldLedgerEntryRepository entries;
    private final Clock clock;

    public GoldService(GoldAccountRepository accounts, GoldLedgerEntryRepository entries, Clock clock) {
        this.accounts = accounts;
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional
    public void openAccount(UUID userId) {
        accounts.save(GoldAccount.forUser(userId, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public BigDecimal gramsOf(UUID userId) {
        return accounts.findByUserId(userId).map(GoldAccount::getGrams)
                .orElseThrow(() -> ApiException.notFound("Gold account"));
    }

    @Transactional(readOnly = true)
    public GoldBalanceResponse balance(UUID userId) {
        return new GoldBalanceResponse(gramsOf(userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<GoldLedgerEntryResponse> ledger(UUID userId, int page, int size) {
        return PageResponse.of(entries.findByGoldAccountId(accountIdOf(userId), Paging.newestFirst(page, size, "createdAt")),
                GoldLedgerEntryResponse::from);
    }

    @Transactional(readOnly = true)
    public GoldLedgerReport reconcile() {
        BigDecimal sum = accounts.sumOfAllBalances();
        long unbalanced = entries.countUnbalancedTransactions();
        long outOfSync = entries.countAccountsOutOfSync();
        return new GoldLedgerReport(sum, unbalanced, outOfSync, sum.signum() == 0 && unbalanced == 0 && outOfSync == 0);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void deliverGold(UUID userId, BigDecimal grams, UUID orderId) {
        transfer(SYSTEM_VAULT, accountIdOf(userId), grams, GoldLedgerEntry.EntryType.BUY, orderId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void takeBackGold(UUID userId, BigDecimal grams, UUID orderId) {
        transfer(accountIdOf(userId), SYSTEM_VAULT, grams, GoldLedgerEntry.EntryType.SELL, orderId);
    }

    private void transfer(UUID fromId, UUID toId, BigDecimal grams, GoldLedgerEntry.EntryType type, UUID ref) {
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

    private UUID accountIdOf(UUID userId) {
        return accounts.findIdByUserId(userId).orElseThrow(() -> ApiException.notFound("Gold account"));
    }
}
