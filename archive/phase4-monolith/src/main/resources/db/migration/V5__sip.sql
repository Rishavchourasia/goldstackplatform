
CREATE TABLE sips (
    id            UUID PRIMARY KEY,
    user_id       UUID           NOT NULL REFERENCES users (id),
    amount        NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    frequency     VARCHAR(10)    NOT NULL CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    start_date    DATE           NOT NULL,
    next_run_date DATE           NOT NULL,
    status        VARCHAR(10)    NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELLED')),
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_sips_due ON sips (status, next_run_date);
CREATE INDEX ix_sips_user ON sips (user_id);

CREATE TABLE sip_executions (
    id              UUID PRIMARY KEY,
    sip_id          UUID         NOT NULL REFERENCES sips (id),
    run_date        DATE         NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    order_id        UUID,
    status          VARCHAR(10)  NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    failure_reason  VARCHAR(255),
    attempted_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_sip_exec_sip ON sip_executions (sip_id, run_date DESC);
