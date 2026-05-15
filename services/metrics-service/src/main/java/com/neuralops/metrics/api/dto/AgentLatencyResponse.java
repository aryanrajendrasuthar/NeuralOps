package com.neuralops.metrics.api.dto;

public record AgentLatencyResponse(
        String agentId,
        double p50Ms,
        double p95Ms,
        double p99Ms,
        long sampleCount
) {}
