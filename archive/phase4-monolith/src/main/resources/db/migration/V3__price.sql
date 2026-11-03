
CREATE TABLE price_history (
    id             BIGSERIAL PRIMARY KEY,
    price_per_gram NUMERIC(12, 2) NOT NULL CHECK (price_per_gram > 0),
    source         VARCHAR(20)    NOT NULL,   -- SIMULATOR or API
    recorded_at    TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_price_history_time ON price_history (recorded_at DESC);

CREATE TABLE daily_prices (
    price_date  DATE PRIMARY KEY,
    close_price NUMERIC(12, 2) NOT NULL CHECK (close_price > 0)
);
