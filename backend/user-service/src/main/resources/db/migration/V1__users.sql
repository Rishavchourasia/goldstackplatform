CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,   -- BCrypt hash, never the raw password
    full_name     VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_users_email ON users (LOWER(email));

CREATE TABLE outbox (
    id           UUID PRIMARY KEY,          -- = the event's eventId
    topic        VARCHAR(100) NOT NULL,
    message_key  VARCHAR(100),
    payload      TEXT         NOT NULL,     -- the event as JSON
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ                -- NULL until it reached Kafka
);
CREATE INDEX ix_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
