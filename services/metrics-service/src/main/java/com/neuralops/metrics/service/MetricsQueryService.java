package com.neuralops.metrics.service;

import com.neuralops.metrics.api.dto.AgentLatencyResponse;
import com.neuralops.metrics.api.dto.AgentStatsResponse;
import com.neuralops.metrics.api.dto.HourlyMetricPoint;
import com.neuralops.metrics.api.dto.OverviewResponse;
import com.neuralops.metrics.domain.repository.AgentMetricRepository;
import com.neuralops.metrics.redis.RedisMetricsStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsQueryService {

    private final RedisMetricsStore redisMetricsStore;
    private final AgentMetricRepository agentMetricRepository;

    public AgentLatencyResponse getLatencyPercentiles(String agentId) {
        Map<String, Double> percentiles = redisMetricsStore.getLatencyPercentiles(agentId);

        double p50 = percentiles.getOrDefault("p50", 0.0);
        double p95 = percentiles.getOrDefault("p95", 0.0);
        double p99 = percentiles.getOrDefault("p99", 0.0);
        long sampleCount = percentiles.containsKey("count")
                ? percentiles.get("count").longValue() : 0L;

        return new AgentLatencyResponse(agentId, p50, p95, p99, sampleCount);
    }

    public AgentStatsResponse getAgentStats(String agentId) {
        Map<String, Object> raw = redisMetricsStore.getAgentStats(agentId);

        long traceCount = parseLong(raw.get("trace_count"), 0L);
        long tokenCount = parseLong(raw.get("token_count"), 0L);
        long errorCount = parseLong(raw.get("error_count"), 0L);
        BigDecimal costTotal = parseBigDecimal(raw.get("cost_usd_total"), BigDecimal.ZERO);

        double errorRate = traceCount > 0
                ? BigDecimal.valueOf((double) errorCount / traceCount * 100)
                    .setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        return new AgentStatsResponse(agentId, traceCount, tokenCount, errorCount, errorRate, costTotal);
    }

    public List<HourlyMetricPoint> getHourlyMetrics(String agentId, int hoursBack) {
        Instant since = Instant.now().minus(hoursBack, ChronoUnit.HOURS);
        List<Object[]> rows = agentMetricRepository.findHourlyMetricsForAgent(agentId, since);

        List<HourlyMetricPoint> points = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Instant bucket = row[0] instanceof Instant i ? i : Instant.parse(row[0].toString());
            double p95 = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            double p99 = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            long errors = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            BigDecimal cost = row[4] != null
                    ? BigDecimal.valueOf(((Number) row[4]).doubleValue()) : BigDecimal.ZERO;
            points.add(new HourlyMetricPoint(bucket, p95, p99, errors, cost));
        }
        return points;
    }

    public OverviewResponse getOverview() {
        Map<String, Object> raw = redisMetricsStore.getOverview();
        long activeAgents = parseLong(raw.get("active_agents"), 0L);
        long totalTracesToday = parseLong(raw.get("total_traces_today"), 0L);
        return new OverviewResponse(activeAgents, totalTracesToday);
    }

    private long parseLong(Object value, long defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            log.warn("Could not parse long from Redis value '{}': {}", value, ex.getMessage());
            return defaultValue;
        }
    }

    private BigDecimal parseBigDecimal(Object value, BigDecimal defaultValue) {
        if (value == null) return defaultValue;
        try {
            return new BigDecimal(value.toString()).setScale(8, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            log.warn("Could not parse BigDecimal from Redis value '{}': {}", value, ex.getMessage());
            return defaultValue;
        }
    }
}
