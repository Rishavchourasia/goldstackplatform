
CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    user_id         UUID           NOT NULL,       -- users live in user-service's database: no foreign key across services
    side            VARCHAR(4)     NOT NULL CHECK (side IN ('BUY', 'SELL')),
    source          VARCHAR(10)    NOT NULL CHECK (source IN ('MANUAL', 'SIP')),
    quote_id        UUID,                          -- NULL for SIP orders
    idempotency_key VARCHAR(100)   NOT NULL,
    coins           NUMERIC(19, 2) NOT NULL,
    grams           NUMERIC(19, 4) NOT NULL,
    price_per_gram  NUMERIC(12, 2) NOT NULL,
    status          VARCHAR(10)    NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    failure_code    VARCHAR(40),
    failure_reason  VARCHAR(255),
    sip_id          UUID,                          -- set for SIP installments
    run_date        DATE,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
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
    entry_type      VARCHAR(20)    NOT NULL,   -- BUY, SELL or SELL_REVERSAL (saga compensation)
    reference_id    UUID,
    created_at      TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_gold_ledger_account_time ON gold_ledger_entries (gold_account_id, created_at DESC);
CREATE INDEX ix_gold_ledger_tx           ON gold_ledger_entries (transaction_id);

INSERT INTO gold_accounts (id, user_id, account_type, grams, version, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000101', NULL, 'SYSTEM_VAULT', 0, 0, NOW());

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
