package com.bank.transaction;

import com.bank.transaction.dto.ConcurrencyTestResult;
import com.bank.transaction.service.ConcurrencyEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MandatoryConcurrencyTest {

    @Autowired
    private ConcurrencyEngine concurrencyEngine;

    @Test
    @DisplayName("Mandatory Scenario: Account ₹10,000 with concurrent ₹7,000 & ₹5,000 withdrawals must not both succeed")
    void testConcurrentWithdrawalsPreserveBalanceInvariant() throws InterruptedException {
        ConcurrencyTestResult result = concurrencyEngine.runMandatoryConcurrencyScenario();

        // 1. Invariant must be satisfied
        assertTrue(result.invariantSatisfied(), "Concurrency invariant failed: " + result.summary());

        // 2. Final balance must be non-negative
        assertTrue(result.finalBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Final balance cannot be negative: " + result.finalBalance());

        // 3. Exactly one thread must have succeeded
        long successes = result.threadDetails().stream()
                .filter(t -> "SUCCESS".equalsIgnoreCase(t.status()))
                .count();
        assertEquals(1, successes, "Exactly one withdrawal must succeed");

        // 4. Expected final balance must be either 3000 (if 7000 succeeded) or 5000 (if 5000 succeeded)
        boolean validFinalBalance = result.finalBalance().compareTo(new BigDecimal("3000.00")) == 0 ||
                                    result.finalBalance().compareTo(new BigDecimal("5000.00")) == 0;
        assertTrue(validFinalBalance, "Final balance must be exactly ₹3,000 or ₹5,000, but was: " + result.finalBalance());
    }

    @RepeatedTest(5)
    @DisplayName("Stress Invariant Check: Repeat concurrent withdrawal test 5 times consecutively")
    void testRepeatedConcurrentWithdrawals() throws InterruptedException {
        ConcurrencyTestResult result = concurrencyEngine.runMandatoryConcurrencyScenario();
        assertTrue(result.invariantSatisfied());
        assertTrue(result.finalBalance().compareTo(BigDecimal.ZERO) >= 0);
    }
}
