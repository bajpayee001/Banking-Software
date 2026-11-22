package com.bank.transaction;

import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.exception.DuplicateTransactionException;
import com.bank.transaction.model.Account;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IdempotencyTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("Idempotency: Repeated transaction ID must not update balances twice")
    void testRepeatedTransactionIdRejectedWithoutDuplicateImpact() {
        String testTxnId = "TXN-IDEMP-" + UUID.randomUUID();
        String targetAccount = "ACC2004";

        Account beforeAcc = accountRepository.findById(targetAccount).orElseThrow();
        BigDecimal initialBalance = beforeAcc.getBalance();
        BigDecimal depositAmount = new BigDecimal("2500.00");

        // 1. First execution - Must succeed
        TransactionRequest req1 = new TransactionRequest(
                testTxnId, null, targetAccount, depositAmount, TransactionType.DEPOSIT
        );
        TransactionResponse resp1 = transactionService.processTransaction(req1);
        assertNotNull(resp1);
        assertEquals(initialBalance.add(depositAmount), resp1.remainingDestinationBalance());

        // Verify balance in database updated once
        Account intermediateAcc = accountRepository.findById(targetAccount).orElseThrow();
        assertEquals(initialBalance.add(depositAmount), intermediateAcc.getBalance());

        // 2. Second execution with identical transactionId - Must fail with DuplicateTransactionException
        TransactionRequest duplicateReq = new TransactionRequest(
                testTxnId, null, targetAccount, depositAmount, TransactionType.DEPOSIT
        );

        assertThrows(DuplicateTransactionException.class, () -> {
            transactionService.processTransaction(duplicateReq);
        });

        // 3. Verify balance in database was NOT incremented a second time
        Account finalAcc = accountRepository.findById(targetAccount).orElseThrow();
        assertEquals(initialBalance.add(depositAmount), finalAcc.getBalance(),
                "Balance must remain unchanged after duplicate transaction request");
    }
}
