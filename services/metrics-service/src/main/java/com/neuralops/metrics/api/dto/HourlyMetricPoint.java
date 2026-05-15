package com.neuralops.metrics.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record HourlyMetricPoint(
        Instant bucket,
        double p95Ms,
        double p99Ms,
        long errorCount,
        BigDecimal costUsd
) {}
