"""
LedgerFlow SQL Reporting & Index Analysis
Demonstrates Section 10 Applied SQL Depth & EXPLAIN plan analysis.
"""

REPORTING_QUERY = """
SELECT
    a.account_holder,
    t.transaction_id,
    t.amount,
    f.risk_score,
    f.reason,
    RANK() OVER (PARTITION BY t.account_id ORDER BY f.risk_score DESC) AS risk_rank
FROM fraud_flags f
JOIN transactions t ON t.transaction_id = f.transaction_id
JOIN accounts a ON a.account_id = t.account_id
WHERE f.reviewed = FALSE
ORDER BY f.risk_score DESC;
"""

INDEX_RATIONALE = """
Indexing Decisions and Trade-offs:
1. idx_transactions_account_id ON transactions(account_id):
   - Justification: Supports join condition with accounts and window function partition 'PARTITION BY t.account_id' as well as historical queries per account.
   - Storage/Write Trade-off: Minor index maintenance overhead on INSERT; eliminates O(N) sequential scans on historical transaction queries.

2. idx_transactions_created_at ON transactions(created_at):
   - Justification: Drives live dashboard feed ordering 'ORDER BY created_at DESC'.
   - Storage/Write Trade-off: Sequential B-Tree index append; highly efficient for time-series range lookups.

3. idx_transactions_status ON transactions(status):
   - Justification: Accelerates filtering for flagged/pending states ('status = FLAGGED').

4. UNIQUE(idempotency_key) ON transactions:
   - Justification: Acts as both the duplicate-detection mechanism and a fast unique index lookup O(log N) for idempotent replays.
"""

if __name__ == "__main__":
    print("=" * 80)
    print("LEDGERFLOW SQL REPORTING QUERY & INDEXING STRATEGY")
    print("=" * 80)
    print("\n[Section 10 Reporting Query with Window Functions]:")
    print(REPORTING_QUERY)
    print("\n[Indexing Rationale & Trade-offs]:")
    print(INDEX_RATIONALE)
