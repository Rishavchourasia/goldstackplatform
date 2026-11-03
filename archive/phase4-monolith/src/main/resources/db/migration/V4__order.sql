
CREATE TABLE quotes (
    id             UUID PRIMARY KEY,
    user_id        UUID           NOT NULL REFERENCES users (id),
    side           VARCHAR(4)     NOT NULL CHECK (side IN ('BUY', 'SELL')),
    price_per_gram NUMERIC(12, 2) NOT NULL,
    coins          NUMERIC(19, 2) NOT NULL CHECK (coins > 0),
    grams          NUMERIC(19, 4) NOT NULL CHECK (grams > 0),
    expires_at     TIMESTAMPTZ    NOT NULL,
    used           BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ    NOT NULL
);

CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    user_id         UUID           NOT NULL REFERENCES users (id),
    side            VARCHAR(4)     NOT NULL CHECK (side IN ('BUY', 'SELL')),
    source          VARCHAR(10)    NOT NULL CHECK (source IN ('MANUAL', 'SIP')),
    quote_id        UUID REFERENCES quotes (id),   -- NULL for SIP orders
    idempotency_key VARCHAR(100)   NOT NULL,
    coins           NUMERIC(19, 2) NOT NULL,
    grams           NUMERIC(19, 4) NOT NULL,
    price_per_gram  NUMERIC(12, 2) NOT NULL,
    status          VARCHAR(10)    NOT NULL,       -- COMPLETED (PENDING/FAILED used by the Saga in Phase 5)
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ux_orders_user_idem UNIQUE (user_id, idempotency_key)
);
CREATE INDEX ix_orders_user_time ON orders (user_id, created_at DESC);

CREATE TABLE gold_accounts (
    id           UUID PRIMARY KEY,
    user_id      UUID UNIQUE,                 -- NULL for the system vault
    account_type VARCHAR(20)    NOT NULL,     -- USER or SYSTEM_VAULT
    grams        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    version      BIGINT         NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_gold_user_non_negative CHECK (account_type <> 'USER' OR grams >= 0),
    CONSTRAINT ck_gold_user_has_owner    CHECK ((account_type = 'USER') = (user_id IS NOT NULL))
);
CREATE UNIQUE INDEX ux_gold_accounts_system_type ON gold_accounts (account_type) WHERE user_id IS NULL;

CREATE TABLE gold_ledger_entries (
    id              UUID PRIMARY KEY,
    transaction_id  UUID           NOT NULL,
    gold_account_id UUID           NOT NULL REFERENCES gold_accounts (id),
    direction       VARCHAR(6)     NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    grams           NUMERIC(19, 4) NOT NULL CHECK (grams > 0),
    balance_after   NUMERIC(19, 4) NOT NULL,
    entry_type      VARCHAR(20)    NOT NULL,   -- BUY or SELL
    reference_id    UUID,
    created_at      TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_gold_ledger_account_time ON gold_ledger_entries (gold_account_id, created_at DESC);
CREATE INDEX ix_gold_ledger_tx           ON gold_ledger_entries (transaction_id);

INSERT INTO gold_accounts (id, user_id, account_type, grams, version, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000101', NULL, 'SYSTEM_VAULT', 0, 0, NOW());
