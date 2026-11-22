package com.bank.transaction.service;

import com.bank.transaction.dto.ChatbotQueryRequest;
import com.bank.transaction.dto.ChatbotQueryResponse;
import com.bank.transaction.model.Account;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatbotService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("(?i)(ACC\\d{4})");
    private static final Pattern TXN_PATTERN = Pattern.compile("(?i)(TXN[a-zA-Z0-9\\-_]+)");

    public ChatbotService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public ChatbotQueryResponse answerCustomerQuery(ChatbotQueryRequest request) {
        String query = request.query() != null ? request.query().trim() : "";
        String lowerQuery = query.toLowerCase();

        String detectedAccountId = extractAccountId(query, request.accountId());
        String detectedTxnId = extractTransactionId(query);

        // 1. Balance Inquiry
        if (lowerQuery.contains("balance") || lowerQuery.contains("how much money") || lowerQuery.contains("funds")) {
            if (detectedAccountId != null) {
                Optional<Account> accountOpt = accountRepository.findById(detectedAccountId.toUpperCase());
                if (accountOpt.isPresent()) {
                    Account acc = accountOpt.get();
                    String answer = String.format("Account **%s** (%s) has an available balance of **₹%s %s**. Current Status: **%s**.",
                            acc.getAccountId(), acc.getAccountNumber(), acc.getBalance().toPlainString(), acc.getCurrency(), acc.getStatus());
                    return new ChatbotQueryResponse(answer, "BALANCE_INQUIRY",
                            List.of("Can I withdraw ₹5,000 from " + acc.getAccountId() + "?",
                                    "Transfer ₹1,000 from " + acc.getAccountId() + " to ACC2004",
                                    "Show recent transactions for " + acc.getAccountId()),
                            LocalDateTime.now());
                } else {
                    return new ChatbotQueryResponse("I couldn't find an account with ID '" + detectedAccountId + "'. Available accounts include ACC1001, ACC2004, ACC1002.",
                            "ACCOUNT_NOT_FOUND",
                            List.of("Check balance of ACC1001", "List available demo accounts"),
                            LocalDateTime.now());
                }
            } else {
                return new ChatbotQueryResponse("To check your balance, please mention your Account ID (e.g., 'What is the balance of ACC1001?').",
                        "BALANCE_PROMPT",
                        List.of("Check balance of ACC1001", "Check balance of ACC2004", "Check balance of ACC1002"),
                        LocalDateTime.now());
            }
        }

        // 2. Transaction Status Inquiry
        if (lowerQuery.contains("status") || lowerQuery.contains("check transaction") || lowerQuery.contains("track")) {
            if (detectedTxnId != null) {
                Optional<Transaction> txnOpt = transactionRepository.findById(detectedTxnId);
                if (txnOpt.isPresent()) {
                    Transaction txn = txnOpt.get();
                    String answer = String.format("Transaction **%s** (%s of ₹%s) was completed with status: **%s** on %s.",
                            txn.getTransactionId(), txn.getType(), txn.getAmount(), txn.getStatus(), txn.getCreatedAt());
                    return new ChatbotQueryResponse(answer, "TRANSACTION_STATUS",
                            List.of("Show audit log for " + txn.getTransactionId(),
                                    "What is the balance of ACC1001?"),
                            LocalDateTime.now());
                } else {
                    return new ChatbotQueryResponse("No transaction record was found for ID '" + detectedTxnId + "'. Please check the transaction ID and try again.",
                            "TRANSACTION_NOT_FOUND",
                            List.of("Check status of TXN-INIT-001", "Run concurrency test"),
                            LocalDateTime.now());
                }
            } else {
                return new ChatbotQueryResponse("Please provide the Transaction ID you would like to track (e.g., 'status of TXN-INIT-001').",
                        "TXN_PROMPT",
                        List.of("Check status of TXN-INIT-001", "Check status of TXN-INIT-002"),
                        LocalDateTime.now());
            }
        }

        // 3. Concurrency / Invariant Questions
        if (lowerQuery.contains("concurrent") || lowerQuery.contains("two withdrawals") || lowerQuery.contains("race condition") || lowerQuery.contains("deadlock")) {
            String answer = """
                    **Safe Concurrency Assurance:**
                    Our transaction processing engine utilizes **Deterministic Lock Ordering** (always locking the lower account ID first) combined with **Pessimistic Row-Level Locking (`SELECT ... FOR UPDATE`)**.
                    
                    When two simultaneous withdrawals arrive (e.g. ₹7,000 and ₹5,000 on an account with ₹10,000), the thread pool queues the operations safely. Exactly one withdrawal commits, leaving a positive balance of ₹3,000 or ₹5,000, while the other is rejected with `InsufficientBalanceException`. Account balances are mathematically protected from negative or dirty values!
                    """;
            return new ChatbotQueryResponse(answer, "CONCURRENCY_EXPLANATION",
                    List.of("Run the live ₹10,000 concurrency scenario now",
                            "Explain deterministic locking order",
                            "How does idempotency prevent duplicate debits?"),
                    LocalDateTime.now());
        }

        // 4. Idempotency Questions
        if (lowerQuery.contains("duplicate") || lowerQuery.contains("idempotent") || lowerQuery.contains("double charge")) {
            String answer = """
                    **Idempotent Transaction Guarantee:**
                    Every incoming request carries a unique `transactionId`. If a network glitch or mobile app retries the same transaction ID:
                    1. Application layer checks if the ID was already recorded.
                    2. Database enforces a `PRIMARY KEY / UNIQUE` constraint on `transaction_id`.
                    3. The repeated request is rejected with `DuplicateTransactionException` without updating your balance a second time!
                    """;
            return new ChatbotQueryResponse(answer, "IDEMPOTENCY_EXPLANATION",
                    List.of("Check balance of ACC1001", "Run concurrency test"),
                    LocalDateTime.now());
        }

        // 5. Transfer / How-to questions
        if (lowerQuery.contains("transfer") || lowerQuery.contains("deposit") || lowerQuery.contains("withdraw") || lowerQuery.contains("how to")) {
            String answer = """
                    **How Money Movements Work:**
                    - **Deposit**: Select destination account and amount. Credits instantly upon database commit.
                    - **Withdrawal**: Debits funds only if account is `ACTIVE` and has sufficient balance.
                    - **Transfer**: Atomically transfers money from source to destination. Both accounts are updated within a single ACID transaction boundary.
                    """;
            return new ChatbotQueryResponse(answer, "HOW_TO_GUIDE",
                    List.of("Check balance of ACC1001", "Check balance of ACC2004", "Run ₹10,000 concurrency test"),
                    LocalDateTime.now());
        }

        // Default greeting / overview
        String welcomeAnswer = """
                Hello! 👋 I am your **Autonomous Banking Assistant**. I can help you with:
                - Checking live account balances (e.g., *"What is the balance of ACC1001?"*)
                - Tracking transaction status (e.g., *"Status of TXN-INIT-001"*)
                - Explaining concurrency controls, deadlock prevention, and idempotency
                - Reviewing transaction error reasons and audit records
                
                How may I assist you today?
                """;

        return new ChatbotQueryResponse(welcomeAnswer, "GENERAL_HELP",
                List.of("What is the balance of ACC1001?",
                        "What happens during concurrent withdrawals?",
                        "Run the mandatory concurrency test",
                        "Show Top 10 accounts by volume"),
                LocalDateTime.now());
    }

    private String extractAccountId(String query, String fallback) {
        if (fallback != null && !fallback.isBlank()) return fallback;
        Matcher matcher = ACCOUNT_PATTERN.matcher(query);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    private String extractTransactionId(String query) {
        Matcher matcher = TXN_PATTERN.matcher(query);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
