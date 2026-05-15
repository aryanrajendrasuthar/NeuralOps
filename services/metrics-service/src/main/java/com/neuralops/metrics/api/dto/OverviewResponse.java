package com.neuralops.metrics.api.dto;

public record OverviewResponse(
        long activeAgents,
        long totalTracesToday
) {}
