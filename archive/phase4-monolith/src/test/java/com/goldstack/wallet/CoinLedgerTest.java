package com.goldstack.wallet;

import com.goldstack.common.LedgerDirection;
import com.goldstack.common.error.InsufficientFundsException;
import com.goldstack.wallet.CoinLedgerEntry.EntryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CoinLedgerTest {

    @Mock
    WalletRepository wallets;
    @Mock
    CoinLedgerEntryRepository entries;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC);
    private CoinLedger ledger;

    private Wallet user;
    private Wallet treasury;

    @BeforeEach
    void setUp() {
        ledger = new CoinLedger(wallets, entries, clock);
        user = Wallet.forUser(UUID.randomUUID(), clock.instant());
        user.credit(new BigDecimal("1000.00"), clock.instant());
        treasury = systemWallet(WalletService.SYSTEM_TREASURY, Wallet.AccountType.SYSTEM_TREASURY);

        lenient().when(wallets.lockAllInIdOrder(anyCollection())).thenReturn(List.of(user, treasury));
    }

    @Test
    void transferWritesOneDebitAndOneMatchingCredit() {
        UUID orderId = UUID.randomUUID();
        UUID txId = ledger.transfer(user.getId(), treasury.getId(), new BigDecimal("400.00"), EntryType.BUY, orderId);

        ArgumentCaptor<CoinLedgerEntry> saved = ArgumentCaptor.forClass(CoinLedgerEntry.class);
        verify(entries, times(2)).save(saved.capture());
        CoinLedgerEntry debit = saved.getAllValues().get(0);
        CoinLedgerEntry credit = saved.getAllValues().get(1);

        assertThat(debit.getTransactionId()).isEqualTo(txId).isEqualTo(credit.getTransactionId());
        assertThat(debit.getDirection()).isEqualTo(LedgerDirection.DEBIT);
        assertThat(credit.getDirection()).isEqualTo(LedgerDirection.CREDIT);
        assertThat(debit.getAmount()).isEqualTo(credit.getAmount()).isEqualTo(new BigDecimal("400.00"));
        assertThat(debit.getWalletId()).isEqualTo(user.getId());
        assertThat(credit.getWalletId()).isEqualTo(treasury.getId());
        assertThat(debit.getReferenceId()).isEqualTo(orderId);

        assertThat(user.getBalance()).isEqualTo(new BigDecimal("600.00"));
        assertThat(treasury.getBalance()).isEqualTo(new BigDecimal("400.00"));
        assertThat(debit.getBalanceAfter()).isEqualTo(user.getBalance());
        assertThat(credit.getBalanceAfter()).isEqualTo(treasury.getBalance());

        assertThat(user.getBalance().add(treasury.getBalance())).isEqualByComparingTo("1000.00");
    }

    @Test
    void userWalletCannotGoNegativeAndNothingIsWritten() {
        assertThatThrownBy(() ->
                ledger.transfer(user.getId(), treasury.getId(), new BigDecimal("1000.01"), EntryType.BUY, null))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("1000.00");

        verify(entries, never()).save(any());
        assertThat(user.getBalance()).isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    void systemAccountMayGoNegative() {

        ledger.transfer(treasury.getId(), user.getId(), new BigDecimal("250.00"), EntryType.SELL, null);
        assertThat(treasury.getBalance()).isEqualTo(new BigDecimal("-250.00"));
        assertThat(user.getBalance()).isEqualTo(new BigDecimal("1250.00"));
    }

    @Test
    void rejectsZeroOrNegativeAmounts() {
        assertThatThrownBy(() -> ledger.transfer(user.getId(), treasury.getId(), BigDecimal.ZERO, EntryType.BUY, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Wallet systemWallet(UUID id, Wallet.AccountType type) {
        Wallet w = Wallet.forUser(UUID.randomUUID(), Instant.EPOCH);
        ReflectionTestUtils.setField(w, "id", id);
        ReflectionTestUtils.setField(w, "userId", null);
        ReflectionTestUtils.setField(w, "accountType", type);
        return w;
    }
}
