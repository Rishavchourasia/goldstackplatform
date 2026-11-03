
CREATE TABLE wallets (
    id           UUID PRIMARY KEY,
    user_id      UUID UNIQUE,                 -- NULL for system accounts
    account_type VARCHAR(30)    NOT NULL,     -- USER, SYSTEM_BONUS, SYSTEM_TOPUP, SYSTEM_TREASURY
    balance      NUMERIC(19, 2) NOT NULL DEFAULT 0,
    version      BIGINT         NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_wallet_user_non_negative CHECK (account_type <> 'USER' OR balance >= 0),
    CONSTRAINT ck_wallet_user_has_owner    CHECK ((account_type = 'USER') = (user_id IS NOT NULL))
);

CREATE UNIQUE INDEX ux_wallets_system_type ON wallets (account_type) WHERE user_id IS NULL;

CREATE TABLE coin_ledger_entries (
    id             UUID PRIMARY KEY,
    transaction_id UUID           NOT NULL,   -- the DEBIT and CREDIT of one movement share this
    wallet_id      UUID           NOT NULL REFERENCES wallets (id),
    direction      VARCHAR(6)     NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount         NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    balance_after  NUMERIC(19, 2) NOT NULL,
    entry_type     VARCHAR(20)    NOT NULL,   -- SIGNUP_BONUS, TOPUP, BUY, SELL, REFUND
    reference_id   UUID,                      -- e.g. the order that caused it
    created_at     TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ix_coin_ledger_wallet_time ON coin_ledger_entries (wallet_id, created_at DESC);
CREATE INDEX ix_coin_ledger_tx          ON coin_ledger_entries (transaction_id);

INSERT INTO wallets (id, user_id, account_type, balance, version, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000001', NULL, 'SYSTEM_BONUS',    0, 0, NOW()),
    ('00000000-0000-0000-0000-000000000002', NULL, 'SYSTEM_TOPUP',    0, 0, NOW()),
    ('00000000-0000-0000-0000-000000000003', NULL, 'SYSTEM_TREASURY', 0, 0, NOW());

CREATE INDEX ix_coin_ledger_reference ON coin_ledger_entries (reference_id, entry_type);

CREATE TABLE outbox (
    id           UUID PRIMARY KEY,          -- = the event's eventId
    topic        VARCHAR(100) NOT NULL,
    message_key  VARCHAR(100),
    payload      TEXT         NOT NULL,     -- the event as JSON
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ                -- NULL until it reached Kafka
);
CREATE INDEX ix_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL
);
