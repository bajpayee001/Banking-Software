-- ========================================================
-- Multi-Threaded Banking System: Initial Seed Data
-- ========================================================

-- Customers
INSERT INTO CUSTOMER (customer_id, first_name, last_name, email, phone, created_at) VALUES
('CUST101', 'Rahul', 'Sharma', 'rahul.sharma@example.com', '+91-9876543210', CURRENT_TIMESTAMP),
('CUST102', 'Priya', 'Patel', 'priya.patel@example.com', '+91-9876543211', CURRENT_TIMESTAMP),
('CUST103', 'Amit', 'Verma', 'amit.verma@example.com', '+91-9876543212', CURRENT_TIMESTAMP),
('CUST104', 'Neha', 'Gupta', 'neha.gupta@example.com', '+91-9876543213', CURRENT_TIMESTAMP),
('CUST105', 'Vikram', 'Singh', 'vikram.singh@example.com', '+91-9876543214', CURRENT_TIMESTAMP);

-- Accounts
-- ACC1001: Exactly ₹10,000 for mandatory concurrency test scenario
INSERT INTO ACCOUNT (account_id, customer_id, account_number, balance, status, currency, created_at, updated_at) VALUES
('ACC1001', 'CUST101', 'SB-10001001', 10000.00, 'ACTIVE', 'INR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ACC2004', 'CUST102', 'SB-10002004', 25000.00, 'ACTIVE', 'INR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ACC1002', 'CUST101', 'CA-10001002', 50000.00, 'ACTIVE', 'INR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ACC2001', 'CUST102', 'SB-10002001', 100000.00, 'ACTIVE', 'INR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ACC3001', 'CUST103', 'SB-10003001', 15000.00, 'FROZEN', 'INR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ACC4001', 'CUST104', 'SB-10004001', 0.00, 'ACTIVE', 'INR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ACC5001', 'CUST105', 'SB-10005001', 75000.00, 'ACTIVE', 'INR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Baseline Historical Transactions
INSERT INTO TRANSACTION (transaction_id, source_account, destination_account, amount, type, status, created_at) VALUES
('TXN-INIT-001', NULL, 'ACC1001', 10000.00, 'DEPOSIT', 'SUCCESS', CURRENT_TIMESTAMP),
('TXN-INIT-002', NULL, 'ACC2004', 25000.00, 'DEPOSIT', 'SUCCESS', CURRENT_TIMESTAMP),
('TXN-INIT-003', 'ACC1001', 'ACC2004', 1500.00, 'TRANSFER', 'SUCCESS', CURRENT_TIMESTAMP);

-- Baseline Audit Entries
INSERT INTO TRANSACTION_AUDIT (transaction_id, operation_type, thread_name, duration_ms, status, recorded_at) VALUES
('TXN-INIT-001', 'DEPOSIT', 'MainInitializerThread', 12, 'SUCCESS', CURRENT_TIMESTAMP),
('TXN-INIT-002', 'DEPOSIT', 'MainInitializerThread', 9, 'SUCCESS', CURRENT_TIMESTAMP),
('TXN-INIT-003', 'TRANSFER', 'MainInitializerThread', 18, 'SUCCESS', CURRENT_TIMESTAMP);
