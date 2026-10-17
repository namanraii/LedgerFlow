-- LedgerFlow seed accounts. Idempotent so a Postgres volume can restart
-- without minting a duplicate book of business on every boot.
INSERT INTO accounts (account_holder, balance, created_at)
SELECT 'Alice Smith (Institutional)', 10000.00, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE account_holder = 'Alice Smith (Institutional)');

INSERT INTO accounts (account_holder, balance, created_at)
SELECT 'Bob Jones (Retail)', 5000.00, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE account_holder = 'Bob Jones (Retail)');

INSERT INTO accounts (account_holder, balance, created_at)
SELECT 'Charlie Brown (High-Net-Worth)', 50000.00, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE account_holder = 'Charlie Brown (High-Net-Worth)');

INSERT INTO accounts (account_holder, balance, created_at)
SELECT 'Concurrency Benchmark Target', 10000.00, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE account_holder = 'Concurrency Benchmark Target');
