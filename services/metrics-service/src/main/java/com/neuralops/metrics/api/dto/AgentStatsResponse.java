package com.neuralops.metrics.api.dto;

import java.math.BigDecimal;

public record AgentStatsResponse(
        String agentId,
        long traceCount,
        long tokenCount,
        long errorCount,
        double errorRatePct,
        BigDecimal costUsdTotal
) {}
