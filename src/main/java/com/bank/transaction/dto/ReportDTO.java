package com.bank.transaction.dto;

import java.util.List;
import java.util.Map;

public record ReportDTO(
    int reportId,
    String reportTitle,
    String sqlQuery,
    List<String> columns,
    List<Map<String, Object>> rows,
    String explainPlan
) {}
