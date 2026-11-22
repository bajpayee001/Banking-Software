package com.bank.transaction.service.ai;

import com.bank.transaction.dto.FraudAssessmentResult;
import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.model.Account;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.repository.AccountRepository;
import com.bank.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    private static final double FRAUD_THRESHOLD = 0.85;

    public FraudDetectionService(TransactionRepository transactionRepository, AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Pre-commit AI anomaly & risk scoring model.
     * Computes multi-feature risk assessment:
     * 1. Velocity spike factor (frequency of recent operations)
     * 2. Transaction magnitude deviation
     * 3. Radical balance depletion anomaly
     */
    public FraudAssessmentResult assessRisk(TransactionRequest request) {
        String txId = request.getTransactionId() != null ? request.getTransactionId() : "TXN-UNKNOWN";
        String targetAccount = request.getSourceAccount() != null ? request.getSourceAccount() : request.getDestinationAccount();
        BigDecimal amount = request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO;

        List<String> riskFactors = new ArrayList<>();
        double score = 0.05; // baseline low risk

        if (targetAccount == null) {
            return new FraudAssessmentResult(txId, null, amount, 0.95, true, "CRITICAL",
                    List.of("Unspecified account target"), LocalDateTime.now());
        }

        Optional<Account> accOpt = accountRepository.findById(targetAccount);
        if (accOpt.isPresent()) {
            Account acc = accOpt.get();

            // Feature 1: High Velocity Anomaly Check (recent transactions on account)
            List<Transaction> recent = transactionRepository.findRecentByAccount(targetAccount, PageRequest.of(0, 10));
            long lastMinuteCount = recent.stream()
                    .filter(t -> t.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1)))
                    .count();

            if (lastMinuteCount >= 4) {
                score += 0.45;
                riskFactors.add("High velocity burst: " + lastMinuteCount + " transactions detected in the past 60s");
            } else if (lastMinuteCount >= 2) {
                score += 0.20;
                riskFactors.add("Elevated velocity: " + lastMinuteCount + " transactions in the past 60s");
            }

            // Feature 2: Extreme Outlier Amount Anomaly
            if (amount.compareTo(new BigDecimal("100000.00")) >= 0) {
                score += 0.40;
                riskFactors.add("High monetary value spike: transaction exceeds ₹100,000 threshold");
            } else if (amount.compareTo(new BigDecimal("50000.00")) >= 0) {
                score += 0.25;
                riskFactors.add("Substantial monetary amount: ₹" + amount);
            }

            // Feature 3: Radical Account Drain Anomaly (withdraws > 95% of current funds)
            if (acc.getBalance().compareTo(BigDecimal.ZERO) > 0 && request.getType() != null &&
                    request.getType().name().equals("WITHDRAWAL")) {
                BigDecimal ratio = amount.divide(acc.getBalance(), 4, java.math.RoundingMode.HALF_UP);
                if (ratio.compareTo(new BigDecimal("0.95")) >= 0 && amount.compareTo(new BigDecimal("5000.00")) > 0) {
                    score += 0.35;
                    riskFactors.add("Complete account depletion pattern: withdrawal requests " +
                            ratio.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "% of balance");
                }
            }
        }

        // Clamp score between 0.00 and 1.00
        double finalScore = Math.min(1.00, Math.round(score * 100.0) / 100.0);
        boolean flagged = finalScore >= FRAUD_THRESHOLD;
        String level = flagged ? "CRITICAL" : (finalScore >= 0.50 ? "MEDIUM" : "LOW");

        log.info("[AI-FRAUD-ASSESSMENT] txId={}, account={}, amount={}, score={}, flagged={}, level={}",
                txId, targetAccount, amount, finalScore, flagged, level);

        return new FraudAssessmentResult(
                txId,
                targetAccount,
                amount,
                finalScore,
                flagged,
                level,
                riskFactors.isEmpty() ? List.of("Standard non-anomalous pattern detected") : riskFactors,
                LocalDateTime.now()
        );
    }
}
