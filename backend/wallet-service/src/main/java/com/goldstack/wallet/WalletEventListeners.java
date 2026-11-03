package com.goldstack.wallet;

import com.goldstack.common.events.Events.OrderFailed;
import com.goldstack.common.events.Events.OrderPlaced;
import com.goldstack.common.events.Events.UserRegistered;
import com.goldstack.common.events.Topics;
import com.goldstack.common.outbox.IdempotentConsumer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class WalletEventListeners {

    private final IdempotentConsumer consumer;
    private final WalletService wallet;

    WalletEventListeners(IdempotentConsumer consumer, WalletService wallet) {
        this.consumer = consumer;
        this.wallet = wallet;
    }

    @KafkaListener(topics = Topics.USER_REGISTERED)
    void onUserRegistered(String payload) {
        consumer.handle(Topics.USER_REGISTERED, payload, UserRegistered.class, wallet::grantSignupBonus);
    }

    @KafkaListener(topics = Topics.ORDER_PLACED)
    void onOrderPlaced(String payload) {
        consumer.handle(Topics.ORDER_PLACED, payload, OrderPlaced.class, wallet::settle);
    }

    @KafkaListener(topics = Topics.ORDER_FAILED)
    void onOrderFailed(String payload) {
        consumer.handle(Topics.ORDER_FAILED, payload, OrderFailed.class, wallet::refund);
    }
}
