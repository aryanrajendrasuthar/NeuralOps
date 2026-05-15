package com.neuralops.costanalytics.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record CostSummaryResponse(
        String agentId,
        BigDecimal totalCostUsd,
        long totalTraceCount,
        long totalTokenCount,
        List<DailyCostPoint> dailyBreakdown
) {}
