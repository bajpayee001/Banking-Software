package com.bank.transaction.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TRANSACTION_AUDIT")
public class TransactionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "transaction_id", length = 64, nullable = false)
    private String transactionId;

    @Column(name = "operation_type", length = 20, nullable = false)
    private String operationType;

    @Column(name = "thread_name", length = 100, nullable = false)
    private String threadName;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public TransactionAudit() {}

    public TransactionAudit(String transactionId, String operationType, String threadName,
                            Long durationMs, String status, LocalDateTime recordedAt) {
        this.transactionId = transactionId;
        this.operationType = operationType;
        this.threadName = threadName;
        this.durationMs = durationMs;
        this.status = status;
        this.recordedAt = recordedAt;
    }

    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }

    public String getThreadName() { return threadName; }
    public void setThreadName(String threadName) { this.threadName = threadName; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
