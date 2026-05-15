package com.neuralops.metrics.api.controller;

import com.neuralops.metrics.api.dto.AgentLatencyResponse;
import com.neuralops.metrics.api.dto.AgentStatsResponse;
import com.neuralops.metrics.api.dto.HourlyMetricPoint;
import com.neuralops.metrics.api.dto.OverviewResponse;
import com.neuralops.metrics.service.MetricsQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "Real-time and historical metrics for AI agents")
public class MetricsController {

    private final MetricsQueryService metricsQueryService;

    @GetMapping("/agents/{agentId}/latency")
    @Operation(
            summary = "Get latency percentiles for an agent",
            description = "Returns real-time p50, p95, and p99 latency percentiles computed " +
                    "from the sliding window of the last 10,000 trace events stored in Redis."
    )
    public ResponseEntity<AgentLatencyResponse> getLatency(
            @PathVariable @Parameter(description = "Unique agent identifier") String agentId) {
        return ResponseEntity.ok(metricsQueryService.getLatencyPercentiles(agentId));
    }

    @GetMapping("/agents/{agentId}/stats")
    @Operation(
            summary = "Get cumulative stats for an agent",
            description = "Returns trace count, token count, error count, error rate, " +
                    "and total cost from Redis accumulators."
    )
    public ResponseEntity<AgentStatsResponse> getStats(
            @PathVariable @Parameter(description = "Unique agent identifier") String agentId) {
        return ResponseEntity.ok(metricsQueryService.getAgentStats(agentId));
    }

    @GetMapping("/agents/{agentId}/hourly")
    @Operation(
            summary = "Get hourly metric history for an agent",
            description = "Returns per-hour p95/p99 latency, error count, and cost from the " +
                    "TimescaleDB continuous aggregate. Default lookback is 24 hours."
    )
    public ResponseEntity<List<HourlyMetricPoint>> getHourlyMetrics(
            @PathVariable @Parameter(description = "Unique agent identifier") String agentId,
            @RequestParam(defaultValue = "24") @Parameter(description = "Hours of history to return (max 168)") int hours) {
        int safeHours = Math.min(Math.max(hours, 1), 168);
        return ResponseEntity.ok(metricsQueryService.getHourlyMetrics(agentId, safeHours));
    }

    @GetMapping("/overview")
    @Operation(
            summary = "Get platform-wide overview counters",
            description = "Returns total active agents and total traces ingested today from Redis."
    )
    public ResponseEntity<OverviewResponse> getOverview() {
        return ResponseEntity.ok(metricsQueryService.getOverview());
    }
}
