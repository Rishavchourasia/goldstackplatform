package com.goldstack.notification;

import com.goldstack.common.error.ApiException;
import com.goldstack.common.web.PageResponse;
import com.goldstack.common.web.Paging;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    void notify(UUID userId, String type, String message) {
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

