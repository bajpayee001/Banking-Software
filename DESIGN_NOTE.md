# Technical Design Note: Transaction Boundaries, Locking, Idempotency & Indexing

## 1. Transaction Boundaries & Atomicity
Financial transactions must strictly adhere to **ACID** properties. In this service:
- Database transactions use **`READ_COMMITTED`** isolation level via Spring's `TransactionTemplate` and `@Transactional`.
- Explicit transaction boundaries begin **before balance evaluation** and commit **only after all party accounts and the `TRANSACTION` entity are persisted**.
- **Audit Isolation (`REQUIRES_NEW`)**: The monetary state changes are decoupled from the audit/telemetry records. If a transaction fails or rolls back (e.g. `InsufficientBalanceException`), the balance remains intact while the failure audit is committed in an independent isolated transaction (`Propagation.REQUIRES_NEW`).

## 2. Locking Strategy & Deadlock Prevention
### Pessimistic Row Locking (`SELECT ... FOR UPDATE`)
We use pessimistic row-level locking via JPA (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on the `ACCOUNT` table:
- **Why chosen over Optimistic Locking**: Under high-volume banking contention, optimistic locking results in frequent `OptimisticLockingFailureException` and high rollback/retry costs. Pessimistic locking queues competing threads deterministically.
- **Why it prevents Lost Updates & Dirty Outcomes**: No concurrent thread can read or write to an account's balance until the owning transaction commits or aborts.
- **Why it prevents Negative Balances**: Combined with the database check constraint `CHECK (balance >= 0.00)` and application-level pre-debit check (`balance.compareTo(amount) >= 0`), an account balance can never drop below zero.

### Deterministic Lock Ordering (Eliminating Deadlocks)
Bidirectional transfers present a classic **Circular Wait** deadlock risk:
- *Thread 1*: Transfers from Account A -> Account B (Locks A, waits for B)
- *Thread 2*: Transfers from Account B -> Account A (Locks B, waits for A)

**Solution**:
Our `TransactionService` and `AccountLockManager` enforce a strict **lexicographical lock ordering**:
$$\text{firstLock} = \min(\text{acc}_A, \text{acc}_B), \quad \text{secondLock} = \max(\text{acc}_A, \text{acc}_B)$$
Both threads will always acquire the lock for Account A first, and Account B second. This breaks Coffman's circular wait condition, guaranteeing **100% deadlock-free concurrent transfers**.

## 3. Idempotency Guarantees
A client, ATM terminal, or network timeout may cause the same transaction payload to be transmitted multiple times.
1. **Application-Level Check**: Pre-flight verification query checks `transactionRepository.existsById(transactionId)`. If present, an immediate `DuplicateTransactionException` is thrown without altering balances.
2. **Database-Level Constraint**: The `transaction_id` column is defined as `PRIMARY KEY` (or `UNIQUE NOT NULL`). Even in extreme race conditions where duplicate requests bypass application memory checks simultaneously, the database aborts the second insert via unique key violation.
3. **Automated Verification**: Proved by `IdempotencyTest.java`.

## 4. Index Justification & Workload Optimization
The schema implements five justified indexes:
1. `idx_account_customer` on `ACCOUNT(customer_id)`:
   - Eliminates full-table scans when querying all accounts belonging to a customer or generating customer transaction statements.
2. `idx_txn_source_created` on `TRANSACTION(source_account, created_at)`:
   - High-cardinality composite index optimizing queries that filter by source account and sort by timestamp descending.
3. `idx_txn_dest_created` on `TRANSACTION(destination_account, created_at)`:
   - Optimizes reverse transfer lookup and recipient credit statement queries.
4. `idx_txn_status` on `TRANSACTION(status)`:
   - Optimizes reconciliation and failure reporting batch jobs.
5. `idx_failed_category` on `FAILED_TRANSACTION(error_category)`:
   - Accelerates Report #2 (Failed transactions grouped by category/reason).
