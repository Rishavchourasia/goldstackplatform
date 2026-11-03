package com.goldstack.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "notifications")
class Notification {

    enum Type { WELCOME, ORDER_COMPLETED, SIP_SUCCESS, SIP_FAILED }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    Notification(UUID userId, Type type, String message, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type.name();
        this.message = message;
        this.createdAt = createdAt;
    }

    void markRead() {
        this.read = true;
    }

    UUID getId() {
        return id;
    }

    String getType() {
        return type;
    }

    String getMessage() {
        return message;
    }

    boolean isRead() {
        return read;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}

interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying
    @Query("update Notification n set n.read = true where n.userId = :userId and n.read = false")
    int markAllRead(@Param("userId") UUID userId);
}
