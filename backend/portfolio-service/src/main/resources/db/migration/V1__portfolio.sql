CREATE TABLE portfolio_positions (
    user_id        UUID PRIMARY KEY,
    total_grams    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_invested NUMERIC(19, 2) NOT NULL DEFAULT 0,   -- cost basis (average cost method)
    updated_at     TIMESTAMPTZ    NOT NULL
);

CREATE TABLE portfolio_transactions (
    order_id       UUID PRIMARY KEY,                   -- = order-service's order id (dedups replays)
    user_id        UUID           NOT NULL,
    side           VARCHAR(4)     NOT NULL,
    source         VARCHAR(10)    NOT NULL,
    coins          NUMERIC(19, 2) NOT NULL,
    grams          NUMERIC(19, 4) NOT NULL,
    price_per_gram NUMERIC(12, 2) NOT NULL,
    completed_at   TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_portfolio_tx_user_time ON portfolio_transactions (user_id, completed_at DESC);

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL
);
