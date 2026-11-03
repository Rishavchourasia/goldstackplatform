CREATE TABLE portfolio_positions (
    user_id        UUID PRIMARY KEY REFERENCES users (id),
    total_grams    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_invested NUMERIC(19, 2) NOT NULL DEFAULT 0,   -- cost basis (average cost method)
    updated_at     TIMESTAMPTZ    NOT NULL
);

CREATE TABLE notifications (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id),
    type       VARCHAR(30)  NOT NULL,
    message    VARCHAR(500) NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_notifications_user_time ON notifications (user_id, created_at DESC);
