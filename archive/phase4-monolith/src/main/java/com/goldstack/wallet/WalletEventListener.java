package com.goldstack.wallet;

import com.goldstack.user.UserRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class WalletEventListener {

    private final WalletService walletService;

    WalletEventListener(WalletService walletService) {
        this.walletService = walletService;
    }

    @EventListener
    void onUserRegistered(UserRegisteredEvent event) {
        walletService.openWalletWithBonus(event.userId());
    }
}
