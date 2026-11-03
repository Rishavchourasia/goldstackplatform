package com.goldstack.order;

import com.goldstack.user.UserRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class OrderEventListener {

    private final GoldService goldService;

    OrderEventListener(GoldService goldService) {
        this.goldService = goldService;
    }

    @EventListener
    void onUserRegistered(UserRegisteredEvent event) {
        goldService.openAccount(event.userId());
    }
}
