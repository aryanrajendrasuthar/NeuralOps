package com.neuralops.traceingestion.kafka;

import com.neuralops.traceingestion.api.dto.TraceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record TraceEventMessage(
    String traceId,
    String agentId,
    String sessionId,
    TraceType traceType,
    Map<String, Object> payload,
    Long latencyMs,
    Integer tokenCount,
    BigDecimal estimatedCostUsd,
    Instant eventTimestamp,
    Instant ingestedAt,
    Map<String, String> metadata
) {}
