package com.goldstack.wallet;

import com.goldstack.common.security.AuthUser;
import com.goldstack.common.web.PageResponse;
import com.goldstack.wallet.WalletDtos.LedgerEntryResponse;
import com.goldstack.wallet.WalletDtos.TopUpRequest;
import com.goldstack.wallet.WalletDtos.WalletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
@Tag(name = "Wallet (virtual coins)")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    @Operation(summary = "My coin balance")
    public WalletResponse get(@AuthenticationPrincipal AuthUser user) {
        return walletService.getWallet(user.id());
    }

    @PostMapping("/topup")
    @Operation(summary = "Mock top-up: add virtual coins (no real payment)")
    public WalletResponse topUp(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody TopUpRequest req) {
        return walletService.topUp(user.id(), req.amount());
    }

    @GetMapping("/ledger")
    @Operation(summary = "My coin ledger entries, newest first")
    public PageResponse<LedgerEntryResponse> ledger(@AuthenticationPrincipal AuthUser user,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return walletService.ledger(user.id(), page, size);
    }
}
