package com.goldstack.notification;

import com.goldstack.common.security.AuthUser;
import com.goldstack.common.web.PageResponse;
import com.goldstack.notification.NotificationService.NotificationResponse;
import com.goldstack.notification.NotificationService.UnreadCount;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal AuthUser user,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return service.list(user.id(), page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCount unread(@AuthenticationPrincipal AuthUser user) {
        return service.unreadCount(user.id());
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return service.markRead(user.id(), id);
    }

    @PostMapping("/read-all")
    public UnreadCount markAllRead(@AuthenticationPrincipal AuthUser user) {
        return service.markAllRead(user.id());
    }
}
