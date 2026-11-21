# 15-Day Master Execution Plan: Multi-Threaded Banking Transaction Processing System

**Platform / Tech Stack:** Java 17+ (OpenJDK 21 LTS), Spring Boot 3.3.x, Spring Data JPA, HikariCP, PostgreSQL / H2 In-Memory (PostgreSQL Compatibility Mode), SLF4J + Logback, JUnit 5, React (Vite/Tailwind), Maven build structure.  
**Repository:** [https://github.com/bajpayee001/Banking-Software.git](https://github.com/bajpayee001/Banking-Software.git)

---

## 🧭 7 Phases at a Glance

| Phase | Days | Focus Area | Core Objective |
| :--- | :--- | :--- | :--- |
| **Phase 1** | **Days 1–2** | 🏗️ **Foundation** | Normalized schema, DDL constraints, indexes, JPA domain entities, repositories |
| **Phase 2** | **Days 3–4** | 🔐 **Auth & 2FA** | Spring Security, JWT authentication, Admin & User roles, 2FA OTP verification |
| **Phase 3** | **Days 5–7** | 💰 **Core Banking** | Deposit, Withdrawal, Transfer services, validation gate, custom exceptions, audit trail |
| **Phase 4** | **Days 8–9** | ⚡ **Concurrency** | Bounded thread pool, deterministic lock ordering, idempotency, ₹10,000 double-debit test |
| **Phase 5** | **Days 10–11** | 🤖 **AI + WebSocket + Logging** | Gemini AI fraud detection, high-contention router, real-time push, structured JSON logs |
| **Phase 6** | **Days 12–13** | 🖥️ **React Frontend** | Modern User & Admin dashboards, transaction simulator UI, live audit visualizer |
| **Phase 7** | **Days 14–15** | 📊 **Perf + Docs** | 50,000 transaction benchmark (1/5/10/20 threads), 5 SQL reports, EXPLAIN ANALYZE, final README |

---

## 🗂️ Project File Structure (Maven Standard)

```
bank-transaction-system/
├── pom.xml                                   # Root Maven Configuration
├── MASTER-PLAN-15-DAYS.md                    # Single Definitive 15-Day Roadmap
├── README.md                                 # Full Setup & Demonstration Guide
├── DESIGN_NOTE.md                            # Locking, Concurrency, and Indexing Rationale
├── src/
│   ├── main/
│   │   ├── java/com/bank/transaction/
│   │   │   ├── BankTransactionApplication.java
│   │   │   ├── config/                       # ThreadPool, Security, WebSocket configs
│   │   │   ├── controller/                   # REST Endpoints (Auth, Core, Concurrency, Reports, AI)
│   │   │   ├── dto/                          # Requests, Responses, and Report DTOs
│   │   │   ├── exception/                    # Custom Exceptions & Global Exception Handler
│   │   │   ├── model/                        # JPA Entities (Customer, Account, Transaction, Audit, FailedTxn, User, Role)
│   │   │   ├── repository/                   # Spring Data JPA Repositories
│   │   │   ├── security/                     # JWT filter, UserDetails, 2FA services
│   │   │   └── service/                      # Banking Engine, Concurrency, AI, Benchmarks, Reports
│   │   └── resources/
│   │       ├── application.yml               # Application properties & Hikari pool config
│   │       ├── application-postgres.yml      # PostgreSQL production profile
│   │       ├── db/
│   │       │   ├── schema.sql                # Normalized DDL with constraints & indexes
│   │       │   └── seed.sql                  # Initial account and customer test dataset
│   │       └── static/                       # Built web application & static assets
│   └── test/
│       └── java/com/bank/transaction/
│           ├── Day1FoundationTest.java
│           ├── IdempotencyTest.java
│           ├── MandatoryConcurrencyTest.java
│           └── TransferDeadlockTest.java
```

---

## 📅 Day-by-Day Execution Plan

### Phase 1: 🏗️ Foundation (Days 1–2)
- **Day 1: Database Schema, Relational Constraints, and Core JPA Entities**
  - **Estimated Time:** 4–5 hours | **Priority:** P0 (Must Have)
  - **Tasks:**
    1. Define Maven `pom.xml` with Spring Boot 3.3.5, JPA, Validation, H2, PostgreSQL, JUnit 5.
    2. Write normalized DDL (`schema.sql`) for `CUSTOMER`, `ACCOUNT`, `TRANSACTION`, `TRANSACTION_AUDIT`, `FAILED_TRANSACTION`.
    3. Add table constraints: `CHECK (balance >= 0.00)`, `CHECK (amount > 0.00)`, status checks.
    4. Create workload-justified indexes (`customer_id`, `account_number`, `source_account`, `created_at`).
    5. Implement JPA Entities (`Customer`, `Account`, `AccountStatus`, `Transaction`, `TransactionType`, `TransactionStatus`, `TransactionAudit`, `FailedTransaction`).
    6. Implement Spring Data JPA repositories with custom query methods.
  - **Checkpoint Day 1:** `Day1FoundationTest` passes — Schema deploys cleanly, constraints enforce negative balance prevention, entities map without errors.
  - **Daily Git Commit:** `feat(phase-1): day 1 - database schema, jpa entities, repositories, and master plan`

- **Day 2: Seed Data, Repository Queries & Basic Data Access Layer Verification**
  - **Estimated Time:** 3–4 hours | **Priority:** P0 (Must Have)
  - **Tasks:**
    1. Populate `seed.sql` with multi-channel test accounts (active, frozen, zero balance, high net-worth).
    2. Add specific test account `ACC1001` with exactly ₹10,000 for mandatory concurrency test.
    3. Implement custom repository queries for account lookups, customer transaction histories, and balance updates.
    4. Verify Spring Boot context startup and database initialization.
  - **Checkpoint Day 2:** All seed records loaded and verified via automated repository tests.

---

### Phase 2: 🔐 Auth & 2FA (Days 3–4)
- **Day 3: User/Admin Authentication, Role-Based Access Control (RBAC)**
  - **Estimated Time:** 4–5 hours | **Priority:** P1
  - **Tasks:**
    1. Add Spring Security & JWT dependencies.
    2. Create `User` and `Role` entities (`ROLE_USER`, `ROLE_ADMIN`).
    3. Build `JwtAuthenticationFilter`, `JwtTokenProvider`, and `SecurityConfig`.
    4. Implement `/api/auth/login` and `/api/auth/register` endpoints.
  - **Checkpoint Day 3:** JWT issued on valid login; authenticated endpoints reject unauthenticated requests with 401.

- **Day 4: Two-Factor Authentication (2FA) & Role-Secured Endpoints**
  - **Estimated Time:** 3–4 hours | **Priority:** P1
  - **Tasks:**
    1. Implement TOTP/OTP verification service for 2FA.
    2. Secure admin endpoints (benchmark trigger, system-wide reports) for `ROLE_ADMIN` only.
    3. Secure user endpoints (deposit, withdraw, transfer, statement) for `ROLE_USER`.
  - **Checkpoint Day 4:** Role-based access control tests verify user vs admin permission boundaries.

---

### Phase 3: 💰 Core Banking (Days 5–7)
- **Day 5: Validation Gate & Account Operations (Deposit & Withdrawal)**
  - **Estimated Time:** 4–5 hours | **Priority:** P0 (Must Have)
  - **Tasks:**
    1. Build `ValidationService` (account exists, account active, amount > 0, balance sufficient).
    2. Implement `InsufficientBalanceException`, `AccountNotFoundException`, `AccountInactiveException`.
    3. Implement atomic `deposit` and `withdraw` in `TransactionService`.
  - **Checkpoint Day 5:** Unit tests verify successful deposits/withdrawals and strict rejection of overdrafts.

- **Day 6: Account-to-Account Transfers with ACID Boundaries**
  - **Estimated Time:** 4–5 hours | **Priority:** P0 (Must Have)
  - **Tasks:**
    1. Implement atomic transfer operation debiting source and crediting destination within a single `@Transactional` boundary.
    2. Ensure monetary rollback occurs if either side fails.
    3. Emit structured execution logs using SLF4J (`transaction_id`, `account`, `duration`, `status`).
  - **Checkpoint Day 6:** Transfer integration test verifies both accounts update or neither does on failure.

- **Day 7: Failure Isolation, Error Categorization & Audit Trail**
  - **Estimated Time:** 3–4 hours | **Priority:** P0 (Must Have)
  - **Tasks:**
    1. Create `AuditService` using `Propagation.REQUIRES_NEW` to record failed transactions even when monetary operations rollback.
    2. Populate `FAILED_TRANSACTION` with error code, category, and diagnostic reason.
    3. Ensure no database commit occurs before returning visible success.
  - **Checkpoint Day 7:** Rejection tests verify that rejected transactions persist in `FAILED_TRANSACTION` while balance remains unchanged.

---

### Phase 4: ⚡ Concurrency & Idempotency (Days 8–9)
- **Day 8: Deterministic Locking & Deadlock Prevention**
  - **Estimated Time:** 5 hours | **Priority:** P0 (Core 20% of Grade)
  - **Tasks:**
    1. Implement deterministic lock acquisition order for transfers: `min(acc1, acc2)` followed by `max(acc1, acc2)`.
    2. Use `SELECT ... FOR UPDATE` via `LockModeType.PESSIMISTIC_WRITE`.
    3. Write `TransferDeadlockTest` executing concurrent reciprocal transfers (A->B and B->A) across 20 threads.
  - **Checkpoint Day 8:** Reciprocal transfer test completes 1,000 concurrent transfers with 0 deadlocks.

- **Day 9: Idempotency Enforcement & The Mandatory ₹10,000 Scenario**
  - **Estimated Time:** 5 hours | **Priority:** P0 (Acceptance Gate)
  - **Tasks:**
    1. Implement database unique constraint and application-level cache for `transaction_id`.
    2. Build `DuplicateTransactionException` handling.
    3. Implement **Mandatory Concurrency Test**: Account starting at ₹10,000, simultaneous withdrawals of ₹7,000 and ₹5,000.
    4. Verify exactly one succeeds, one fails with `INSUFFICIENT_FUNDS`, final balance >= 0.
  - **Checkpoint Day 9:** `MandatoryConcurrencyTest` passes repeatedly with 100% deterministic correctness.

---

### Phase 5: 🤖 AI + WebSocket + Structured Logging (Days 10–11)
- **Day 10: Gemini AI Fraud Detection & Anomaly Analysis**
  - **Estimated Time:** 4–5 hours | **Priority:** P1
  - **Tasks:**
    1. Integrate AI fraud analysis evaluating velocity, sudden balance drain, and unusual amount spikes.
    2. Implement `ContentionRouterService` to queue high-contention accounts.
    3. Autonomous SQL optimization advisor reviewing slow query execution plans.
  - **Checkpoint Day 10:** AI fraud assessment evaluates transactions and flags suspicious activity.

- **Day 11: Real-Time WebSocket Push & Structured JSON Logs**
  - **Estimated Time:** 3–4 hours | **Priority:** P1
  - **Tasks:**
    1. Configure WebSocket endpoint for live transaction updates to the dashboard.
    2. Configure Logback JSON encoder for structured, machine-readable logs.
    3. Sanitize logs to ensure no credentials or sensitive customer PII are logged.
  - **Checkpoint Day 11:** WebSocket messages broadcast on every committed transaction; logs output structured JSON.

---

### Phase 6: 🖥️ Interactive Frontend (Days 12–13)
- **Day 12: React User Portal (Auth, Balances, Transfer UI)**
  - **Estimated Time:** 5 hours | **Priority:** P1
  - **Tasks:**
    1. Set up React frontend application.
    2. Implement User Login & Registration screens.
    3. Build Account Overview, Live Balance Card, Deposit/Withdraw modal, and Transfer Form.
    4. Implement transaction history list with status badges (`SUCCESS`, `FAILED`, `REJECTED`).
  - **Checkpoint Day 12:** End-user can log in, view live balance, and execute transfers directly from the UI.

- **Day 13: Admin Dashboard, Concurrency Simulator & Audit Viewer**
  - **Estimated Time:** 5 hours | **Priority:** P1
  - **Tasks:**
    1. Build Admin Dashboard with system-wide volume metrics.
    2. Create Interactive Concurrency Simulator widget (trigger ₹10,000 double-withdrawal test with visual proof).
    3. Add AI Insights panel and live audit stream table.
  - **Checkpoint Day 13:** Admin dashboard triggers concurrency tests and visualizes real-time audit logs.

---

### Phase 7: 📊 Performance Benchmark, SQL Reports & Deliverables (Days 14–15)
- **Day 14: 50,000 Transaction Benchmark & Bottleneck Analysis**
  - **Estimated Time:** 5 hours | **Priority:** P0 (Slide 12 Deliverable)
  - **Tasks:**
    1. Implement `BenchmarkService` generating 50,000 transactions across 1, 5, 10, and 20 worker threads.
    2. Measure Total Time, TPS, Success Count, Failure Count, and DB wait times.
    3. Document diminishing returns (database locks, HikariCP limits, CPU/IO contention).
  - **Checkpoint Day 14:** Benchmark table generated matching Slide 12 requirements.

- **Day 15: 5 SQL Reports, EXPLAIN ANALYZE, Design Note & Final Polish**
  - **Estimated Time:** 4–5 hours | **Priority:** P0 (Slide 11 & Acceptance Gate)
  - **Tasks:**
    1. Implement the 5 mandatory SQL reports:
       - Report 1: Top 10 accounts by transaction volume
       - Report 2: Failed transactions grouped by reason
       - Report 3: Daily transaction amount by type
       - Report 4: Accounts with more than N transactions
       - Report 5: Complete transaction history for a customer
    2. Execute and document `EXPLAIN ANALYZE` on 3 critical queries.
    3. Finalize `README.md`, `DESIGN_NOTE.md`, ER Diagrams, and verify zero-friction setup.
  - **Checkpoint Day 15:** Full test suite green, README setup verified, all 30+ acceptance criteria satisfied.

---

## ✅ Final Acceptance Gate (30-Point Checklist)

- [ ] **Data Integrity**: Zero balance corruption under high concurrency.
- [ ] **Negative Balance Prevention**: No withdrawal can leave balance < 0.00.
- [ ] **Mandatory Scenario**: Concurrent ₹7,000 + ₹5,000 against ₹10,000 always leaves exactly 1 success.
- [ ] **Idempotency**: Repeated `transaction_id` changes money only once.
- [ ] **Audit Trail**: Every transaction (success, failure, rejection) leaves a durable audit entry.
- [ ] **Deadlock Free**: Deterministic lock ordering guarantees no transfer deadlocks.
- [ ] **Structured Logging**: Timestamp, txId, account, thread, duration, and error category logged.
- [ ] **SQL Proficiency**: Normalized schema, justified indexes, 5 reports, and EXPLAIN ANALYZE documented.
- [ ] **Benchmark**: 50,000 transactions benchmarked across 1, 5, 10, 20 worker threads with TPS curves.
- [ ] **Turnkey Setup**: Reviewer can clone repo and run via standard Maven command.
