CREATE TABLE notifications (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL,
    type       VARCHAR(30)  NOT NULL,
    message    VARCHAR(500) NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_notifications_user_time ON notifications (user_id, created_at DESC);

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL
);
