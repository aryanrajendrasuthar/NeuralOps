package com.neuralops.costanalytics.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyCostPoint(
        LocalDate date,
        BigDecimal totalCostUsd,
        long traceCount,
        long tokenCount
) {}
