package com.goldstack.wallet;

import com.goldstack.common.Money;
import com.goldstack.common.config.GoldStackProperties;
import com.goldstack.common.error.ApiException;
import com.goldstack.common.web.PageResponse;
import com.goldstack.common.web.Paging;
import com.goldstack.wallet.CoinLedgerEntry.EntryType;
import com.goldstack.wallet.WalletDtos.LedgerEntryResponse;
import com.goldstack.wallet.WalletDtos.LedgerReport;
import com.goldstack.wallet.WalletDtos.WalletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class WalletService {

    static final UUID SYSTEM_BONUS = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID SYSTEM_TOPUP = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID SYSTEM_TREASURY = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final WalletRepository wallets;
    private final CoinLedgerEntryRepository entries;
    private final CoinLedger ledger;
    private final GoldStackProperties props;
    private final Clock clock;

    public WalletService(WalletRepository wallets, CoinLedgerEntryRepository entries, CoinLedger ledger,
                         GoldStackProperties props, Clock clock) {
        this.wallets = wallets;
        this.entries = entries;
        this.ledger = ledger;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public void openWalletWithBonus(UUID userId) {
        Wallet wallet = wallets.saveAndFlush(Wallet.forUser(userId, Instant.now(clock)));
        BigDecimal bonus = Money.coins(props.wallet().signupBonus());
        ledger.transfer(SYSTEM_BONUS, wallet.getId(), bonus, EntryType.SIGNUP_BONUS, null);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID userId) {
        Wallet w = wallets.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Wallet"));
        return new WalletResponse(w.getBalance(), w.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public BigDecimal balanceOf(UUID userId) {
        return getWallet(userId).balance();
    }

    @Transactional
    public WalletResponse topUp(UUID userId, BigDecimal amount) {
        BigDecimal coins = Money.coins(amount);
        if (coins.compareTo(props.wallet().maxTopup()) > 0) {
            throw ApiException.badRequest("TOPUP_LIMIT_EXCEEDED",
                    "A single top-up can be at most " + Money.coins(props.wallet().maxTopup()) + " coins");
        }
        ledger.transfer(SYSTEM_TOPUP, walletIdOf(userId), coins, EntryType.TOPUP, null);
        return getWallet(userId);
    }

    @Transactional
    public void payForGold(UUID userId, BigDecimal coins, UUID orderId) {
        ledger.transfer(walletIdOf(userId), SYSTEM_TREASURY, coins, EntryType.BUY, orderId);
    }

    @Transactional
    public void receiveGoldSaleProceeds(UUID userId, BigDecimal coins, UUID orderId) {
        ledger.transfer(SYSTEM_TREASURY, walletIdOf(userId), coins, EntryType.SELL, orderId);
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryResponse> ledger(UUID userId, int page, int size) {
        return PageResponse.of(entries.findByWalletId(walletIdOf(userId), Paging.newestFirst(page, size, "createdAt")),
                LedgerEntryResponse::from);
    }

    @Transactional(readOnly = true)
    public LedgerReport reconcile() {
        BigDecimal sum = wallets.sumOfAllBalances();
        long unbalanced = entries.countUnbalancedTransactions();
        long outOfSync = entries.countWalletsOutOfSync();
        return new LedgerReport(sum, unbalanced, outOfSync, sum.signum() == 0 && unbalanced == 0 && outOfSync == 0);
    }

    private UUID walletIdOf(UUID userId) {
        return wallets.findIdByUserId(userId).orElseThrow(() -> ApiException.notFound("Wallet"));
    }
}
