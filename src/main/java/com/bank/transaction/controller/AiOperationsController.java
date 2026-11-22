package com.bank.transaction.controller;

import com.bank.transaction.dto.AiQueryOptimizationReport;
import com.bank.transaction.dto.ContentionBatchResult;
import com.bank.transaction.dto.FraudAssessmentResult;
import com.bank.transaction.dto.TransactionRequest;
import com.bank.transaction.service.ai.AiSqlOptimizerService;
import com.bank.transaction.service.ai.ContentionRouterService;
import com.bank.transaction.service.ai.FraudDetectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiOperationsController {

    private final FraudDetectionService fraudService;
    private final ContentionRouterService contentionService;
    private final AiSqlOptimizerService sqlOptimizerService;

    public AiOperationsController(FraudDetectionService fraudService,
                                  ContentionRouterService contentionService,
                                  AiSqlOptimizerService sqlOptimizerService) {
        this.fraudService = fraudService;
        this.contentionService = contentionService;
        this.sqlOptimizerService = sqlOptimizerService;
    }

    /**
     * AI Feature 1: Pre-commit Anomaly & Fraud Assessment
     */
    @PostMapping("/fraud/assess")
    public ResponseEntity<FraudAssessmentResult> assessFraudRisk(@RequestBody TransactionRequest request) {
        FraudAssessmentResult result = fraudService.assessRisk(request);
        return ResponseEntity.ok(result);
    }

    /**
     * AI Feature 3: Intelligent High-Contention Transaction Batcher
     */
    @PostMapping("/contention/batch-run")
    public ResponseEntity<ContentionBatchResult> runContentionComparison(
            @RequestParam(defaultValue = "ACC2004") String account,
            @RequestParam(defaultValue = "40") int count) {
        ContentionBatchResult result = contentionService.compareStandardVsIntelligentBatching(account, count);
        return ResponseEntity.ok(result);
    }

    /**
     * AI Feature 4: Autonomous SQL Query & Index Optimizer
     */
    @GetMapping("/sql-optimizer/diagnose")
    public ResponseEntity<AiQueryOptimizationReport> getOptimizationReport() {
        AiQueryOptimizationReport report = sqlOptimizerService.diagnoseAllQueries();
        return ResponseEntity.ok(report);
    }
}
