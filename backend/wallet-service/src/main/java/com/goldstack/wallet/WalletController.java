package com.goldstack.wallet;

import com.goldstack.common.error.ApiException;
import com.goldstack.common.security.AuthUser;
import com.goldstack.common.web.PageResponse;
import com.goldstack.wallet.WalletDtos.LedgerEntryResponse;
import com.goldstack.wallet.WalletDtos.LedgerReport;
import com.goldstack.wallet.WalletDtos.TopUpRequest;
import com.goldstack.wallet.WalletDtos.WalletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@Tag(name = "Wallet (virtual coins)")
public class WalletController {

    private final WalletService walletService;
    private final boolean devEndpoints;

    public WalletController(WalletService walletService, @Value("${goldstack.dev-endpoints.enabled:false}") boolean dev) {
        this.walletService = walletService;
        this.devEndpoints = dev;
    }

    @GetMapping("/api/wallet")
    @Operation(summary = "My coin balance")
    public WalletResponse get(@AuthenticationPrincipal AuthUser user) {
        return walletService.getWallet(user.id());
    }

    @PostMapping("/api/wallet/topup")
    @Operation(summary = "Mock top-up: add virtual coins (no real payment)")
    public WalletResponse topUp(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody TopUpRequest req) {
        return walletService.topUp(user.id(), req.amount());
    }

    @GetMapping("/api/wallet/ledger")
    @Operation(summary = "My coin ledger entries, newest first")
    public PageResponse<LedgerEntryResponse> ledger(@AuthenticationPrincipal AuthUser user,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return walletService.ledger(user.id(), page, size);
    }

    @GetMapping("/internal/wallets/{userId}/balance")
    public BalanceResponse internalBalance(@PathVariable UUID userId) {
        return new BalanceResponse(walletService.balanceOf(userId));
    }

    @GetMapping("/api/dev/ledger/coins")
    @Operation(summary = "Dev: check the coin ledger balances to zero")
    public LedgerReport reconcile() {
        if (!devEndpoints) {
            throw ApiException.notFound("Endpoint");
        }
        return walletService.reconcile();
    }

    public record BalanceResponse(BigDecimal balance) {
    }
}
