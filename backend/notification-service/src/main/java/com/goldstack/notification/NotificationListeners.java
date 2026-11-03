package com.goldstack.notification;

import com.goldstack.common.events.Events.NotificationRequested;
import com.goldstack.common.events.Events.OrderCompleted;
import com.goldstack.common.events.Events.OrderFailed;
import com.goldstack.common.events.Events.SipDebitFailed;
import com.goldstack.common.events.Events.UserRegistered;
import com.goldstack.common.events.Topics;
import com.goldstack.common.outbox.IdempotentConsumer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class NotificationListeners {

    private final IdempotentConsumer consumer;
    private final NotificationService service;

    NotificationListeners(IdempotentConsumer consumer, NotificationService service) {
        this.consumer = consumer;
        this.service = service;
    }

    @KafkaListener(topics = Topics.USER_REGISTERED)
    void onUserRegistered(String payload) {
        consumer.handle(Topics.USER_REGISTERED, payload, UserRegistered.class, e ->
                service.notify(e.userId(), "WELCOME",
                        "Welcome to GoldStack, " + e.fullName() + "! Your signup bonus coins are on their way."));
    }

    @KafkaListener(topics = Topics.ORDER_COMPLETED)
    void onOrderCompleted(String payload) {
        consumer.handle(Topics.ORDER_COMPLETED, payload, OrderCompleted.class, e -> {
            boolean sip = "SIP".equals(e.source());
            String verb = switch (e.side()) {
                case BUY -> "Bought " + e.grams() + " g of gold for " + e.coins() + " coins";
                case SELL -> "Sold " + e.grams() + " g of gold for " + e.coins() + " coins";
            };
            service.notify(e.userId(), sip ? "SIP_SUCCESS" : "ORDER_COMPLETED",
                    (sip ? "SIP installment: " : "") + verb + " at " + e.pricePerGram() + "/g.");
        });
    }

    @KafkaListener(topics = Topics.ORDER_FAILED)
    void onOrderFailed(String payload) {
        consumer.handle(Topics.ORDER_FAILED, payload, OrderFailed.class, e -> {
            if ("SIP".equals(e.source())) {
                return;
            }
            String what = e.side().name().equals("BUY") ? "purchase" : "sale";
            String extra = e.refundCoins() ? " Your " + e.coins() + " coins have been refunded." : "";
            service.notify(e.userId(), "ORDER_FAILED", "Your gold " + what + " failed: " + e.reason() + "." + extra);
        });
    }

    @KafkaListener(topics = Topics.SIP_DEBIT_FAILED)
    void onSipFailed(String payload) {
        consumer.handle(Topics.SIP_DEBIT_FAILED, payload, SipDebitFailed.class, e ->
                service.notify(e.userId(), "SIP_FAILED", "Your SIP installment of " + e.amount() + " coins on "
                        + e.runDate() + " failed (" + e.reason() + "). We'll try again on the next scheduled date."));
    }

    @KafkaListener(topics = Topics.NOTIFICATION_SEND)
    void onNotificationRequested(String payload) {
        consumer.handle(Topics.NOTIFICATION_SEND, payload, NotificationRequested.class, e ->
                service.notify(e.userId(), e.type(), e.message()));
    }
}
