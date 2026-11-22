package com.bank.transaction.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "FAILED_TRANSACTION")
public class FailedTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "failure_id")
    private Long failureId;

    @Column(name = "transaction_id", length = 64, nullable = false)
    private String transactionId;

    @Column(name = "error_category", length = 50, nullable = false)
    private String errorCategory;

    @Column(name = "error_code", length = 50, nullable = false)
    private String errorCode;

    @Column(name = "failure_reason", length = 500, nullable = false)
    private String failureReason;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    public FailedTransaction() {}

    public FailedTransaction(String transactionId, String errorCategory, String errorCode,
                             String failureReason, LocalDateTime failedAt) {
        this.transactionId = transactionId;
        this.errorCategory = errorCategory;
        this.errorCode = errorCode;
        this.failureReason = failureReason;
        this.failedAt = failedAt;
    }

    public Long getFailureId() { return failureId; }
    public void setFailureId(Long failureId) { this.failureId = failureId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getErrorCategory() { return errorCategory; }
    public void setErrorCategory(String errorCategory) { this.errorCategory = errorCategory; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public LocalDateTime getFailedAt() { return failedAt; }
    public void setFailedAt(LocalDateTime failedAt) { this.failedAt = failedAt; }
}
