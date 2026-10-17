-- LedgerFlow Schema (PRD v2 Section 10)

CREATE TABLE IF NOT EXISTS accounts (
    account_id      BIGSERIAL PRIMARY KEY,
    account_holder  VARCHAR(100) NOT NULL,
    balance         NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id          BIGSERIAL PRIMARY KEY,
    idempotency_key         UUID NOT NULL UNIQUE,
    account_id              BIGINT NOT NULL REFERENCES accounts(account_id),
    counterparty_account_id BIGINT,
    amount                  NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    transaction_type        VARCHAR(20) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    risk_score              NUMERIC(5,4),
    fraud_check_status      VARCHAR(20) DEFAULT 'OK',
    memo                    VARCHAR(160),
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Existing volumes created before memo / counterparty shipped.
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS counterparty_account_id BIGINT;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS memo VARCHAR(160);

CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_counterparty ON transactions(counterparty_account_id);

CREATE TABLE IF NOT EXISTS balance_history (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL REFERENCES accounts(account_id),
    transaction_id  BIGINT NOT NULL REFERENCES transactions(transaction_id),
    balance_before  NUMERIC(14,2) NOT NULL,
    balance_after   NUMERIC(14,2) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_balance_history_account_time ON balance_history (account_id, created_at);

CREATE TABLE IF NOT EXISTS fraud_flags (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  BIGINT NOT NULL REFERENCES transactions(transaction_id),
    reason          TEXT NOT NULL,
    risk_score      NUMERIC(5,4) NOT NULL,
    reviewed        BOOLEAN NOT NULL DEFAULT FALSE,
    reviewer_action VARCHAR(20),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fraud_flags_open ON fraud_flags (reviewed, risk_score);
CREATE INDEX IF NOT EXISTS idx_fraud_flags_transaction ON fraud_flags (transaction_id);
