# TransactSphere — Product Requirements & Implementation Plan (v2)

**Document purpose:** This is a complete, self-contained specification for building "TransactSphere," a portfolio project. It is written so that an LLM (or a developer) with zero prior context can read this document alone and implement the entire project correctly, in order, without needing to ask clarifying questions. If you are an LLM reading this to generate code: implement strictly in the phase order given in Section 7, and do not skip the "deliberate race condition" step in Phase 2 or the failure-injection tests in Phase 3.5 — these are required deliverables, not optional polish.

**Changelog from v1:** This version corrects the project's positioning (concurrency/correctness is the headline, not fraud detection), adds an explicit failure-handling and reliability section (Section 6), adds an idempotency design (Section 6.4), and adds a testing-scope section (Section 8) — all in direct response to a stress-test review that found v1 under-weighted the software-engineering evidence (testing, failure handling, reliability, API design trade-offs) that a bank-caliber SWE JD actually asks for.

---

## 1. Background & Motivation

The author is a second-year Computer Science (AI/ML) student preparing for software engineering internship/full-time applications, including roles at large banks (this project was scoped against a Barclays Software Engineer job description). The target JD emphasized technical themes the author's existing portfolio did not clearly demonstrate:

1. Multi-threaded / concurrent programming
2. Microservices architecture — with genuine service boundaries, not just folder-per-service decoration
3. GUI / server component ownership (frontend + backend split)
4. Applied SQL depth (joins, transactions, indexing, isolation — not just schema design)
5. Software-engineering rigor: unit testing, secure coding, scalability/maintainability, reliability/failure handling, risk and control ownership
6. End-user / stakeholder-facing framing (not purely technical-implementation bullets)

TransactSphere is a single project engineered to demonstrate all six, with a strict internal hierarchy of importance (Section 3) so that effort is spent proportionally to what the JD actually values — not spread evenly across every subsystem.

**This is a portfolio/demonstration project**, not a production system. Simplifications are intentional and called out explicitly. The goal is depth on the highest-priority items, not breadth across all of them.

---

## 2. Product Concept & Positioning

**Do not describe this project as "a banking fraud detection system."** That framing is wrong for two reasons: (a) it competes directly with the author's existing, separate ML-based Financial Fraud Detection System project, making both look like the same artifact; (b) it buries the actual hardest, most valuable engineering problem in the project underneath a secondary feature.

**Correct framing (use this language in the README, resume, and interviews):**

> "I engineered a concurrent transaction-processing platform and investigated how database isolation affects correctness under concurrent load."

The system: synthetic transactions are generated at a configurable rate and flow through a pipeline of independently-owned services. Concurrency correctness at the Ledger layer is the central engineering problem. Fraud screening, messaging, and the dashboard are supporting features that give the concurrency problem a realistic context — they are not the point of the project.

---

## 3. Priority Hierarchy (governs where effort goes)

This ordering determines time allocation. When in doubt about where to spend an extra hour, spend it higher on this list.

1. **Concurrency + correctness** — `ExecutorService` → concurrent requests → race condition → PostgreSQL isolation/row-locking → measured fix. This is the centerpiece.
2. **Reliability / failure handling** — what happens when a dependency is slow, down, or returns an error; idempotent retries; no duplicate transactions; no silent data loss. This is what separates "a demo" from "engineering."
3. **Distributed systems / service boundaries** — Ingestion → Fraud → Ledger → RabbitMQ → Notification, with genuine ownership boundaries and justified communication patterns, not just REST endpoints in separate folders.
4. **Backend engineering fundamentals** — REST API design, error handling, idempotency, unit testing on critical paths.
5. **SQL depth** — transactions, indexes, joins, CTEs/window functions, query performance reasoning.
6. **Product/UI** — fraud analyst persona → flagged queue → review → approve/block.

Fraud detection logic itself is intentionally NOT on this list as a standalone priority — it is infrastructure that exists to give priorities 1–3 something realistic to process, and should take the least engineering effort of any component.

---

## 4. Goals and Non-Goals

### Goals
- Demonstrate real concurrent programming: shared mutable state, race conditions, and correct synchronization — not just "using async/await."
- Demonstrate that microservice boundaries are load-bearing, not decorative: each service can fail independently, and the system's behavior under that failure is deliberately designed and tested, not accidental.
- Demonstrate SQL beyond schema design: explicit transactions, row-level locking, indexing decisions justified by query patterns, and at least one non-trivial reporting query (joins + window functions/CTEs).
- Demonstrate baseline secure-coding and input-validation practice (Section 6.5) and critical-path unit testing (Section 8), even though this is a portfolio project.
- Produce a working GUI that a non-technical "analyst" persona could plausibly use, with a stated design rationale tied to a user need.
- Produce concrete, quotable metrics (throughput, error rates before/after a fix, query latency) for resume bullets and interview answers.

### Non-Goals (explicitly out of scope)
- Production-grade auth/encryption/PCI compliance — out of scope; a brief README note acknowledging this is sufficient. Baseline input validation and secrets hygiene (Section 6.5) ARE in scope; full auth/authz is not.
- A sophisticated ML fraud model — a rule-based/statistical scorer is sufficient and deliberate (see Section 2). Do not spend disproportionate effort here.
- Horizontal scaling, container orchestration (Kubernetes), or cloud deployment — local/Docker Compose is sufficient.
- Full Kafka-style event streaming across all services — RabbitMQ scoped to the fraud-alert path only (see Section 5).
- Mobile responsiveness of the dashboard — desktop-only is fine.
- A dedicated deduplication microservice/table for idempotency — a client-generated idempotency key plus a database UNIQUE constraint is sufficient (Section 6.4). Do not over-engineer this.

---

## 5. System Architecture

### 5.1 High-level diagram (textual)

```
[Transaction Generator] --> [Ingestion Service] --(REST, sync)--> [Fraud Detection Service]
                                    |                                        |
                                    | (REST, sync)                          | (if high risk)
                                    v                                        v
                            [Ledger Service] <-------------------- [Message Queue: fraud-alerts]
                              (PostgreSQL)                                   |
                                    ^                                        v
                                    |                              [Notification Service]
                                    |                                        |
                                    +-------- REST (approve/block) ---------[Ops Dashboard] (React)
                                                                              ^
                                                                    (polls or WebSocket)
```

### 5.2 Services (5 total: 4 backend + 1 frontend) and ownership boundaries

Each service must have a clearly stated **ownership boundary** — what data/logic it exclusively owns, and what it explicitly does NOT do. This is what prevents the "5 folders with REST endpoints" failure mode called out in review.

| # | Service | Stack | Owns (exclusively) | Explicitly does NOT do |
|---|---|---|---|---|
| 1 | Transaction Ingestion Service | Java 17+, `java.util.concurrent` | Concurrent intake and orchestration of transaction requests; the thread pool itself | Does not persist anything; does not make fraud/risk decisions; stateless between requests except in-flight orchestration |
| 2 | Fraud Detection Service | Python (FastAPI) | Risk-scoring logic and rules; owns nothing persistent beyond its own in-memory rolling stats | Does not touch the ledger/balances; does not decide whether to block a transaction (it only recommends via risk score — the Ledger/business rule decides accept/reject; see Section 6.2 for why this split matters for failure isolation) |
| 3 | Ledger Service | Java (Spring Boot) or Python (FastAPI) + PostgreSQL | The ONLY service that reads/writes `accounts`, `transactions`, `balance_history`. Owns transactional integrity, idempotency enforcement, and the source of truth for balances | Does not call out to Fraud Detection itself (Ingestion orchestrates that call, not Ledger) — keeps Ledger's dependency graph shallow, which matters for its own reliability |
| 4 | Notification Service | Python or Java, minimal | Delivery of fraud alerts to whatever is listening (dashboard) | Does not decide what counts as fraud; does not write to the `fraud_flags` table (Fraud Detection Service does that via Ledger, so the flag exists even if Notification is down — see Section 6.2, scenario 3) |
| 5 | Ops Dashboard | React + Vite | Presentation and analyst-facing triage workflow only | Contains no business logic — every "approve/block" action is a call to Ledger Service's API, which is the sole authority on transaction state |

**Why these specific boundaries (state this in the README):** the split is drawn along **failure-isolation lines**, not just "different responsibilities." Fraud Detection can be completely down without corrupting the ledger or losing a transaction (it just means transactions proceed without a risk score, logged as such — see Section 6.2). The Ledger Service has the fewest outbound dependencies of any service, because it is the one service that must never be blocked by another service's failure while holding a financial state change.

**Messaging:** RabbitMQ, used ONLY for the fraud-alert path (Fraud Detection Service → Notification Service). All other inter-service calls are synchronous REST (JSON over HTTP). This is a deliberate, justified scope decision — see Section 4 Non-Goals.

**Database:** Single PostgreSQL instance, owned exclusively by the Ledger Service (other services do not query it directly — they go through the Ledger Service's API). This is "database per service" in spirit, even though it's one physical DB instance for simplicity — state this explicitly in the README.

---

## 6. Reliability, Failure Handling & Security (NEW — core deliverable, not an afterthought)

This section exists because architecture that isn't tested against failure is "architectural decoration," per the review that produced this v2. Every scenario below must have (a) a designed/intended behavior, (b) an automated test proving that behavior, and (c) for the two scenarios marked **[LIVE-DEMOABLE]**, a way to reproduce the failure locally by stopping a Docker container, so it can optionally be shown live in an interview.

### 6.1 Failure-scenario matrix

| # | Scenario | Intended behavior | How it's verified |
|---|---|---|---|
| 1 | Fraud Detection Service is down/unreachable when Ingestion calls it | Ingestion treats this as "risk score unknown," logs it, and still forwards the transaction to Ledger with `risk_score = NULL` and a `fraud_check_status = 'UNAVAILABLE'` flag — the transaction is NOT blocked or silently dropped just because a non-critical dependency failed. **[LIVE-DEMOABLE]**: stop the Fraud Detection container and submit a transaction; confirm it still completes. | Automated test: mock Fraud Detection to return a connection error; assert transaction still reaches `COMPLETED` or `FLAGGED_UNKNOWN` status, never silently lost. |
| 2 | Ledger Service call times out from Ingestion | Ingestion retries the Ledger call up to N times (e.g., 3) with exponential backoff, using the SAME idempotency key each retry (see 6.4) so a slow-but-eventually-successful first attempt can't result in a duplicate. If all retries fail, the transaction is marked `FAILED_RETRYABLE` and logged with full context for manual/automated reprocessing — it is never silently dropped. | Automated test: mock Ledger to time out twice then succeed on the 3rd call; assert exactly one transaction record is created (not three), using the idempotency key. |
| 3 | RabbitMQ is unavailable when Fraud Detection tries to publish an alert | The fraud flag is written directly to the Ledger Service's `fraud_flags` table BEFORE attempting the RabbitMQ publish (i.e., the durable record doesn't depend on the queue being up). If the publish fails, log the failure; the dashboard's flagged-queue view reads from the DB via Ledger Service, not from the queue, so the flag is never lost — only the real-time push notification is delayed. **[LIVE-DEMOABLE]**: stop the RabbitMQ container, generate a fraudulent-looking transaction, confirm it still appears in the dashboard's flagged queue (via polling/DB read) even though no push notification fired. | Automated test: mock the RabbitMQ publish call to raise an exception; assert the `fraud_flags` row still exists in the DB regardless. |
| 4 | The same transaction request is submitted twice (client retry, network blip, or double-click) | Client generates a UUID `idempotency_key` per logical transaction attempt. Ledger Service has a UNIQUE constraint on `idempotency_key` in the `transactions` table. A duplicate submission with the same key returns the ORIGINAL transaction's result (HTTP 200 with the existing record), not a new transaction and not an error. See Section 6.4 for full design. | Automated test: submit the same `(idempotency_key, account_id, amount)` twice concurrently; assert only one row exists in `transactions` and both HTTP responses return the same `transaction_id`. |
| 5 | Two threads attempt to debit the same account simultaneously | Resolved via `SELECT ... FOR UPDATE` row-level locking in the Ledger Service (this is the Phase 1/2 centerpiece — see Section 9). One thread's transaction blocks until the other commits; no lost updates. | This IS the before/after experiment in Section 9 — the primary artifact of the whole project. |
| 6 | Account has insufficient funds for a debit | Ledger Service checks balance within the SAME locked transaction as the update (not a separate earlier read) and returns HTTP 422 with a clear error body (`{"error": "INSUFFICIENT_FUNDS", "available_balance": ...}`); the transaction is recorded with `status = 'REJECTED'`, not silently ignored, so it's auditable. | Automated test: attempt to debit more than the account balance; assert 422 response and a `REJECTED` transaction row exists (for audit trail). |
| 7 | A downstream service returns an unexpected 500 | Treated the same as scenario 1/2 depending on which service: non-critical-path failures (Fraud Detection) degrade gracefully; critical-path failures (Ledger) retry-then-fail-loud per scenario 2. In all cases, errors are logged with structured context (transaction ID, service, error) — never swallowed silently. | Automated test per dependency, mocking a 500 response. |

### 6.2 Design rationale: why Fraud Detection failing must never block a transaction, but Ledger failing must

This is the single most important reliability decision in the system, and the author should be able to explain it unprompted in an interview:

- **Fraud Detection is advisory.** Its job is to add information (a risk score) to a transaction. If it's unavailable, the correct behavior is to proceed without that information and flag the gap — a bank does not stop processing all transactions because a fraud model is temporarily offline; it falls back to a degraded mode and flags the gap for review.
- **Ledger is authoritative.** It is the source of truth for money. If it's unavailable, there is no safe "proceed without it" — the alternative (retry-with-idempotency, then fail loud and log) is the only correct behavior, because silently proceeding would mean claiming a transaction succeeded when the balance was never actually updated.

This asymmetry — some dependencies are advisory (fail open) and some are authoritative (fail closed, but safely retryable) — is a core distributed-systems design concept and is exactly the kind of judgment a Barclays-caliber SWE role is testing for.

### 6.3 Retry policy

- Retries apply only to Ingestion → Ledger calls (the critical path) and only for transient errors (timeouts, connection refused, 5xx) — never for 4xx errors (those are correctness rejections, e.g., insufficient funds, and retrying won't change the outcome).
- Exponential backoff: e.g., 100ms, 400ms, 1600ms, then fail.
- Every retry reuses the same idempotency key (Section 6.4) — this is what makes retries safe rather than risky.

### 6.4 Idempotency design (lean implementation, per project scope decision)

- The Ingestion Service generates a UUID `idempotency_key` at the moment a transaction is first created (not per HTTP retry — per logical transaction attempt).
- `transactions` table gets a `UNIQUE` constraint on `idempotency_key`.
- Ledger Service's `POST /transactions` endpoint: attempt the insert; if it fails on the unique constraint, catch that specific error and instead fetch-and-return the existing row with that key (HTTP 200, not 201, to signal "this already existed").
- No separate deduplication service or table — the UNIQUE constraint on the existing `transactions` table is sufficient and is the deliberately lean version of this pattern (see Section 4 Non-Goals). Document this explicitly as a scoped-down version of the pattern used in real payment systems (e.g., Stripe's idempotency keys), so it reads as an informed simplification rather than an oversight.

### 6.5 Baseline secure coding (lean, not full auth/authz)

Out of scope: authentication, authorization, encryption at rest, PCI compliance (Section 4 Non-Goals). In scope, and should actually be implemented:
- Input validation on every REST endpoint (reject negative amounts, malformed account IDs, oversized payloads) with proper 400 responses, not stack traces leaking to the client.
- Parameterized queries / ORM usage only — no string-concatenated SQL anywhere (this is the standard baseline secure-coding practice a code reviewer would check for first).
- No secrets (DB passwords, etc.) committed to the repo — use environment variables / a `.env.example` file with placeholder values.
- Structured logging that never logs full account numbers or amounts in plaintext at INFO level in a way that would be a compliance issue in a real system — log transaction IDs and status, not necessarily every field, and note this restraint in the README as a "if this were production" callout.

---

## 7. Implementation Plan — Phase Order (Do Not Reorder)

### Phase 1 — Ledger Service core + idempotency (build first)
1. Set up PostgreSQL (Docker container: `postgres:16`).
2. Create schema from Section 10, including the `idempotency_key UNIQUE` constraint from Section 6.4.
3. Build REST API:
   - `POST /accounts` — create account (seed with starting balance)
   - `GET /accounts/{id}` — get account + balance
   - `GET /accounts/{id}/transactions` — transaction history (paginated)
   - `POST /transactions` — record a transaction and update balance, honoring `idempotency_key` (Section 6.4) and wrapping the balance check + update in `SELECT ... FOR UPDATE` (Section 9).
   - `GET /reports/flagged` — the reporting query from Section 10.
4. Implement input validation and error responses per Section 6.5.
5. Implement the insufficient-funds check (Section 6.1, scenario 6) inside the same locked transaction.

### Phase 2 — The concurrency experiment (the centerpiece — do not skip or shortcut)
1. Write a throwaway test script firing N concurrent `POST /transactions` requests against the SAME account, against TWO versions of the update logic:
   - **Unsafe version**: read balance, compute new balance in application code, write back — no row lock, no transaction wrapping. Record the lost-update failure rate with exact numbers.
   - **Safe version**: `SELECT ... FOR UPDATE` wrapping read-modify-write in one transaction. Record that the final balance is correct across repeated trials.
2. Keep the unsafe version's code (comment, separate file, or git branch) as evidence — do not delete it. This before/after pair is the single most important artifact from the whole project (Section 9 and Section 11).

### Phase 3 — Transaction Ingestion Service
1. Build a transaction generator (configurable rate, e.g., `TRANSACTIONS_PER_SECOND`).
2. Build the processing engine using `ExecutorService` with a fixed thread pool.
3. Per transaction: generate idempotency key → call Fraud Detection Service (REST) → call Ledger Service `POST /transactions` (passing the idempotency key and, if available, the risk score).
4. Use `CompletableFuture` to chain the fraud-check and ledger-update calls asynchronously.
5. Implement the retry-with-backoff policy from Section 6.3 for the Ledger call specifically.
6. Implement graceful degradation for Fraud Detection failures per Section 6.1 scenario 1.
7. Load-test end-to-end; record throughput and error rate at increasing thread-pool sizes (Section 9).

### Phase 3.5 — Failure-injection test suite (NEW — do this before moving on, not after)
Implement automated tests for every scenario in the Section 6.1 matrix using mocks/stubs for the relevant dependency (do this at the service level with a mocking framework — e.g., WireMock for Java, `unittest.mock`/`responses` for Python — rather than requiring all services running for every test). For scenarios 1 and 3 (marked [LIVE-DEMOABLE]), also manually verify once by stopping the actual Docker container and confirming the behavior, then document the steps to reproduce that demo in the README.

### Phase 4 — Fraud Detection Service
1. Rule-based checks: amount anomaly (z-score vs. account history), velocity check (`ConcurrentHashMap`-based rolling window per account — note this is itself a small concurrency-safe-data-structure talking point), round-number heuristic.
2. Combined risk score (0.0–1.0); if above threshold, publish to RabbitMQ `fraud-alerts` queue AND write the flag via Ledger Service (Section 6.1, scenario 3 — the flag must not depend on the queue).
3. This service should be the fastest phase to build — do not over-invest here per Section 3's priority hierarchy.

### Phase 5 — Messaging + Notification Service
1. Stand up RabbitMQ (`rabbitmq:3-management`, UI on 15672).
2. Fraud Detection publishes to `fraud-alerts`; Notification Service consumes and exposes `GET /alerts` (or WebSocket, optional).

### Phase 6 — Ops Dashboard (React)
1. Live transaction feed (poll or WebSocket).
2. Flagged transaction queue with Approve/Block, calling back to Ledger Service (never contains business logic itself — Section 5.2).
3. Account lookup.
4. Write the "design rationale" paragraph (Section 11, artifact 4).

### Phase 7 — Packaging & Documentation
1. `docker-compose.yml` for PostgreSQL, RabbitMQ, and all 4 backend services.
2. README containing: architecture + ownership boundaries (Section 5), the failure-scenario matrix and how each was tested (Section 6), the before/after concurrency numbers (Section 9), the SQL reporting query, the testing summary (Section 8), and resume-ready bullets (Section 12).
3. Optional: 60-90 second screen capture showing the dashboard, a live failure demo (stopping a container), and the concurrency test output.

---

## 8. Testing Scope (critical paths only — deliberately not exhaustive)

Per project scope decision, unit/integration test coverage is concentrated on the highest-priority logic, not spread evenly:

**Must have solid test coverage:**
- Ledger Service's transaction-update logic: the locked read-modify-write path, insufficient-funds rejection, idempotency-key deduplication (Section 6.4), and the concurrency experiment itself (Section 9) counts as a test artifact in its own right.
- The full failure-scenario matrix (Section 6.1) — one automated test per scenario, minimum.

**Light/best-effort coverage acceptable:**
- Fraud Detection Service's individual rule functions (a handful of unit tests confirming each rule fires correctly on an obvious example is sufficient — do not build an exhaustive test matrix here, per Section 3 priority hierarchy).
- Dashboard components (manual verification is acceptable; automated frontend testing is a nice-to-have, not required).

**Explicitly not required:** end-to-end tests that spin up all 5 services together for every test case — use mocks at service boundaries (Section 7, Phase 3.5) so tests are fast and don't require full Docker Compose just to run the suite.

---

## 9. The Concurrency Experiment (the project's central artifact)

**This is the single most important deliverable in the entire project.** Everything else exists to give it a realistic context.

- Run identical concurrent load (e.g., 100 concurrent debit requests of a fixed amount against one account with a known starting balance) against:
  1. The unsafe implementation (no locking) — record the final balance, the expected balance, and the discrepancy, across multiple trials.
  2. The safe implementation (`SELECT ... FOR UPDATE`) — record that the final balance is correct across the same number of trials.
- Also record throughput (TPS) and error rate for the Ingestion Service end-to-end under increasing thread-pool sizes, separately from the correctness experiment above (correctness and throughput are two different measurements — don't conflate them).
- The author must be able to explain, unprompted and without notes, WHY the race condition happens and WHY the specific fix resolves it (this is Section 12, artifact 5, and is the most likely interview follow-up question).

---

## 10. Data Model (Ledger Service / PostgreSQL)

```sql
CREATE TABLE accounts (
    account_id      SERIAL PRIMARY KEY,
    account_holder  VARCHAR(100) NOT NULL,
    balance         NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE transactions (
    transaction_id  BIGSERIAL PRIMARY KEY,
    idempotency_key UUID NOT NULL UNIQUE,
    account_id      INT NOT NULL REFERENCES accounts(account_id),
    amount          NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    transaction_type VARCHAR(20) NOT NULL, -- 'DEBIT' | 'CREDIT'
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- 'PENDING' | 'COMPLETED' | 'REJECTED' | 'FLAGGED' | 'FAILED_RETRYABLE'
    risk_score      NUMERIC(5,4),
    fraud_check_status VARCHAR(20) DEFAULT 'OK', -- 'OK' | 'UNAVAILABLE'
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_transactions_status ON transactions(status);
-- idempotency_key already has an implicit index via the UNIQUE constraint

CREATE TABLE balance_history (
    id              BIGSERIAL PRIMARY KEY,
    account_id      INT NOT NULL REFERENCES accounts(account_id),
    transaction_id  BIGINT NOT NULL REFERENCES transactions(transaction_id),
    balance_before  NUMERIC(14,2) NOT NULL,
    balance_after   NUMERIC(14,2) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE fraud_flags (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  BIGINT NOT NULL REFERENCES transactions(transaction_id),
    reason          TEXT NOT NULL,
    risk_score      NUMERIC(5,4) NOT NULL,
    reviewed        BOOLEAN NOT NULL DEFAULT FALSE,
    reviewer_action VARCHAR(20), -- 'APPROVED' | 'BLOCKED' | NULL if unreviewed
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
```

**Indexing rationale:** `account_id` and `created_at` are indexed because the dominant query patterns are "all transactions for account X" and "recent transactions" (live feed). `status` is indexed because the flagged-queue view filters continuously on `status = 'FLAGGED'`. `idempotency_key`'s uniqueness constraint doubles as its index, supporting fast duplicate-detection lookups.

**Required reporting query:**

```sql
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
```

---

## 11. Required Artifacts for Resume/Interview Use

1. **The before/after race-condition numbers** (Section 9) — exact figures.
2. **Throughput number** (Section 9) — e.g., "X TPS sustained with Y% error rate at Z threads."
3. **The failure-scenario matrix (Section 6.1) with test results** — this is now as important an artifact as the SQL query.
4. **The SQL reporting query** (Section 10), working, with indexing rationale.
5. **A one-paragraph user-centered design note** for the dashboard (Phase 6).
6. **A short, memorized (not read-from-notes) explanation of**: (a) why the unsafe version had a race condition and why the fix resolves it, AND (b) why Fraud Detection failures fail open while Ledger failures fail closed-but-retryable (Section 6.2) — this second explanation is likely to be a follow-up question precisely because it signals systems-design judgment beyond "I know what a mutex is."

---

## 12. Resume Bullets (use only after artifacts in Section 11 are real)

- "Engineered a concurrent transaction-processing platform in Java, using `ExecutorService`/`CompletableFuture` to sustain [X] TPS, and diagnosed a lost-update race condition in concurrent balance writes — resolved via PostgreSQL row-level locking (`SELECT ... FOR UPDATE`), eliminating balance corruption present in [Y]% of unsynchronized runs."
- "Designed failure-isolation boundaries across a 4-service architecture so that a non-critical dependency (fraud scoring) degrades gracefully while the authoritative ledger service fails safely via idempotent retries — verified with an automated failure-injection test suite covering downstream timeouts, service outages, and duplicate-submission scenarios."
- "Implemented idempotent transaction processing using client-generated idempotency keys and database-level uniqueness constraints, preventing duplicate financial transactions under retry/network-failure conditions."
- "Architected a microservices platform with REST for synchronous calls and RabbitMQ for asynchronous fraud-alert delivery, with each service's data ownership and failure behavior explicitly designed rather than incidental."
- "Wrote PostgreSQL queries using window functions and multi-table joins for fraud/ledger reporting, with an indexing strategy driven by measured dashboard query patterns."
- "Built a React-based operations dashboard for fraud analysts to triage flagged transactions in real time, with the UI's triage-first design informed by a stated user workflow."

---

## 13. Explicit Scope-Cutting Rules (if time runs short)

Cut from the bottom of this list up, never from the top — this list is now ordered by the Section 3 priority hierarchy, not by build order:

1. **(Never cut)** The Section 9 concurrency experiment and its numbers.
2. **(Never cut)** The Section 6.1 failure-scenario matrix and its automated tests — this is the second-most-defensible artifact in the project and was the single biggest gap identified in review.
3. Service boundary rigor can simplify (e.g., Notification Service folded into Fraud Detection Service) but the OWNERSHIP REASONING in Section 5.2 must still be written up even for the simplified version — the explanation is worth more than the extra service.
4. Fraud Detection Service logic can be reduced to just amount-anomaly + round-number checks.
5. RabbitMQ can be replaced with a direct REST call or DB-polling fallback — document the simplification honestly.
6. Dashboard can be reduced to a single page with a table.
7. Docker Compose packaging can be dropped for a manual multi-terminal README section.

---

## 14. Tech Stack Summary

| Layer | Choice | Alternative if needed |
|---|---|---|
| Ingestion Service | Java 17+, `java.util.concurrent` | — (core deliverable, do not substitute) |
| Fraud Detection Service | Python 3.11+, FastAPI | Java + Spring Boot |
| Ledger Service | Java + Spring Boot + Spring Data JPA, or Python + FastAPI + SQLAlchemy | either is fine |
| Database | PostgreSQL 16 | — |
| Messaging | RabbitMQ 3.x | direct REST / DB-polling fallback |
| Notification Service | Python or Java, minimal | can fold into Fraud Detection Service if cutting scope |
| Dashboard | React 18 + Vite | Next.js if preferred |
| Containerization | Docker + Docker Compose | manual multi-terminal run |
| Mocking (tests) | WireMock (Java) / `unittest.mock` or `responses` (Python) | — |

---

## 15. Glossary (for an LLM implementer with no domain context)

- **Race condition / lost update**: when two concurrent operations read the same value, both compute an update based on the stale read, and one write overwrites the other.
- **`SELECT ... FOR UPDATE`**: a PostgreSQL clause that locks selected row(s) until the current transaction commits/rolls back.
- **ExecutorService / CompletableFuture**: Java concurrency primitives for thread-pool task management and chained asynchronous results.
- **Idempotency key**: a client-generated unique identifier for a logical operation attempt, used to safely detect and collapse duplicate submissions/retries into a single effect.
- **Fail open / fail closed**: "fail open" means a dependency's failure doesn't block the primary operation (used for advisory dependencies); "fail closed" means failure blocks the operation rather than risk an incorrect state (used for authoritative dependencies, but paired with safe retries here rather than an outright hard stop).
- **TPS**: transactions per second.

---

*End of document. This spec supersedes v1. If any section is ambiguous during implementation, prefer the interpretation consistent with the Section 3 priority hierarchy and Section 13 scope-cutting rules over adding unscoped complexity.*
