package com.goldstack.notification;

import com.goldstack.common.error.ApiException;
import com.goldstack.common.web.PageResponse;
import com.goldstack.common.web.Paging;
import com.goldstack.order.OrderDtos.OrderCompletedEvent;
import com.goldstack.sip.SipDtos.SipDebitFailedEvent;
import com.goldstack.user.UserRegisteredEvent;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    public record NotificationResponse(UUID id, String type, String message, boolean read, Instant createdAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.isRead(), n.getCreatedAt());
        }
    }

    public record UnreadCount(long unread) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void notify(UUID userId, Notification.Type type, String message) {
        notifications.save(new Notification(userId, type, message, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, int page, int size) {
        return PageResponse.of(notifications.findByUserId(userId, Paging.newestFirst(page, size, "createdAt")),
                NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount(UUID userId) {
        return new UnreadCount(notifications.countByUserIdAndReadFalse(userId));
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID id) {
        Notification n = notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Notification"));
        n.markRead();
        return NotificationResponse.from(n);
    }

    @Transactional
    public UnreadCount markAllRead(UUID userId) {
        notifications.markAllRead(userId);
        return new UnreadCount(0);
    }
}

@Component
class NotificationListener {

    private final NotificationService service;

    NotificationListener(NotificationService service) {
        this.service = service;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onUserRegistered(UserRegisteredEvent e) {
        service.notify(e.userId(), Notification.Type.WELCOME,
                "Welcome to GoldStack, " + e.fullName() + "! Your signup bonus coins are in your wallet.");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onOrderCompleted(OrderCompletedEvent e) {
        boolean sip = "SIP".equals(e.source());
        String verb = switch (e.side()) {
            case BUY -> "Bought " + e.grams() + " g of gold for " + e.coins() + " coins";
            case SELL -> "Sold " + e.grams() + " g of gold for " + e.coins() + " coins";
        };
        service.notify(e.userId(), sip ? Notification.Type.SIP_SUCCESS : Notification.Type.ORDER_COMPLETED,
                (sip ? "SIP installment: " : "") + verb + " at " + e.pricePerGram() + "/g.");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onSipFailed(SipDebitFailedEvent e) {
        service.notify(e.userId(), Notification.Type.SIP_FAILED,
                "Your SIP installment of " + e.amount() + " coins on " + e.runDate()
                        + " failed (" + e.reason() + "). We'll try again on the next scheduled date.");
    }
}
