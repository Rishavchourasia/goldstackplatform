package com.goldstack.wallet;

import com.goldstack.common.Money;
import com.goldstack.common.Side;
import com.goldstack.common.error.ApiException;
import com.goldstack.common.error.InsufficientFundsException;
import com.goldstack.common.events.Events.NotificationRequested;
import com.goldstack.common.events.Events.OrderFailed;
import com.goldstack.common.events.Events.OrderPlaced;
import com.goldstack.common.events.Events.UserRegistered;
import com.goldstack.common.events.Events.WalletSettled;
import com.goldstack.common.events.Events.WalletSettlementFailed;
import com.goldstack.common.events.Topics;
import com.goldstack.common.outbox.OutboxWriter;
import com.goldstack.common.web.PageResponse;
import com.goldstack.common.web.Paging;
import com.goldstack.wallet.CoinLedgerEntry.EntryType;
import com.goldstack.wallet.WalletDtos.LedgerEntryResponse;
import com.goldstack.wallet.WalletDtos.LedgerReport;
import com.goldstack.wallet.WalletDtos.WalletResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class WalletService {

    static final UUID SYSTEM_BONUS = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID SYSTEM_TOPUP = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID SYSTEM_TREASURY = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    @Validated
    @ConfigurationProperties(prefix = "goldstack.wallet")
    public record WalletProperties(@NotNull @Positive BigDecimal signupBonus, @NotNull @Positive BigDecimal maxTopup) {
    }

    private final WalletRepository wallets;
    private final CoinLedgerEntryRepository entries;
    private final CoinLedger ledger;
    private final OutboxWriter outbox;
    private final WalletProperties props;
    private final Clock clock;

    public WalletService(WalletRepository wallets, CoinLedgerEntryRepository entries, CoinLedger ledger,
                         OutboxWriter outbox, WalletProperties props, Clock clock) {
        this.wallets = wallets;
        this.entries = entries;
        this.ledger = ledger;
        this.outbox = outbox;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public WalletResponse getWallet(UUID userId) {
        UUID id = ensureWallet(userId);
        Wallet w = wallets.findById(id).orElseThrow(() -> ApiException.notFound("Wallet"));
        return new WalletResponse(w.getBalance(), w.getUpdatedAt());
    }

    @Transactional
    public BigDecimal balanceOf(UUID userId) {
        return getWallet(userId).balance();
    }

    @Transactional
    public WalletResponse topUp(UUID userId, BigDecimal amount) {
        BigDecimal coins = Money.coins(amount);
        if (coins.compareTo(props.maxTopup()) > 0) {
            throw ApiException.badRequest("TOPUP_LIMIT_EXCEEDED",
                    "A single top-up can be at most " + Money.coins(props.maxTopup()) + " coins");
        }
        ledger.transfer(SYSTEM_TOPUP, ensureWallet(userId), coins, EntryType.TOPUP, null);
        outbox.write(Topics.NOTIFICATION_SEND, userId.toString(), new NotificationRequested(UUID.randomUUID(),
                Instant.now(clock), userId, "TOPUP", "Added " + coins + " coins to your wallet."));
        return getWallet(userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryResponse> ledger(UUID userId, int page, int size) {
        UUID id = wallets.findIdByUserId(userId).orElse(null);
        if (id == null) {
            return new PageResponse<>(java.util.List.of(), page, size, 0, 0);
        }
        return PageResponse.of(entries.findByWalletId(id, Paging.newestFirst(page, size, "createdAt")),
                LedgerEntryResponse::from);
    }

    @Transactional(readOnly = true)
    public LedgerReport reconcile() {
        BigDecimal sum = wallets.sumOfAllBalances();
        long unbalanced = entries.countUnbalancedTransactions();
        long outOfSync = entries.countWalletsOutOfSync();
        return new LedgerReport(sum, unbalanced, outOfSync, sum.signum() == 0 && unbalanced == 0 && outOfSync == 0);
    }

    @Transactional
    public void grantSignupBonus(UserRegistered e) {
        UUID walletId = ensureWallet(e.userId());
        if (entries.existsByWalletIdAndEntryType(walletId, EntryType.SIGNUP_BONUS)) {
            return;
        }
        ledger.transfer(SYSTEM_BONUS, walletId, Money.coins(props.signupBonus()), EntryType.SIGNUP_BONUS, null);
    }

    @Transactional
    public void settle(OrderPlaced e) {
        UUID walletId = ensureWallet(e.userId());
        Instant now = Instant.now(clock);
        try {
            if (e.side() == Side.BUY) {
                ledger.transfer(walletId, SYSTEM_TREASURY, e.coins(), EntryType.BUY, e.orderId());
            } else {
                ledger.transfer(SYSTEM_TREASURY, walletId, e.coins(), EntryType.SELL, e.orderId());
            }
        } catch (InsufficientFundsException ex) {
            log.info("Order {} rejected: {}", e.orderId(), ex.getMessage());
            outbox.write(Topics.WALLET_SETTLEMENT_FAILED, e.orderId().toString(), new WalletSettlementFailed(
                    UUID.randomUUID(), now, e.orderId(), e.userId(), e.side(), ex.getCode(), ex.getMessage()));
            return;
        }
        outbox.write(Topics.WALLET_SETTLED, e.orderId().toString(),
                new WalletSettled(UUID.randomUUID(), now, e.orderId(), e.userId(), e.side()));
    }

    @Transactional
    public void refund(OrderFailed e) {
        if (!e.refundCoins() || e.side() != Side.BUY) {
            return;
        }
        if (!entries.existsByReferenceIdAndEntryType(e.orderId(), EntryType.BUY)
                || entries.existsByReferenceIdAndEntryType(e.orderId(), EntryType.REFUND)) {
            return;
        }
        ledger.transfer(SYSTEM_TREASURY, ensureWallet(e.userId()), e.coins(), EntryType.REFUND, e.orderId());
        log.info("Refunded {} coins for failed order {}", e.coins(), e.orderId());
    }

    private UUID ensureWallet(UUID userId) {
        wallets.insertIfMissing(UUID.randomUUID(), userId, Instant.now(clock));
        return wallets.findIdByUserId(userId).orElseThrow(() -> ApiException.notFound("Wallet"));
    }
}
