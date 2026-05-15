package com.neuralops.metrics.redis;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * All real-time metric state is stored in Redis.
 *
 * Key conventions:
 *   agent:stats:{agentId}           → Hash of scalar stats (trace count, token count, cost total)
 *   agent:latency:sorted:{agentId}  → Sorted set of latency_ms values (score = latency_ms, member = traceId:ts)
 *   agent:lastseen:{agentId}        → String, ISO timestamp
 *   metrics:overview                → Hash of global counters (active_agents, total_traces_today)
 *
 * Latency percentiles are computed by ranking position in the sorted set:
 *   p50 = element at rank (count * 0.50)
 *   p95 = element at rank (count * 0.95)
 *   p99 = element at rank (count * 0.99)
 */
@Slf4j
@Component
public class RedisMetricsStore {

    private static final String STATS_KEY_PREFIX = "agent:stats:";
    private static final String LATENCY_SORTED_KEY_PREFIX = "agent:latency:sorted:";
    private static final String LAST_SEEN_KEY_PREFIX = "agent:lastseen:";
    private static final String OVERVIEW_KEY = "metrics:overview";
    private static final String ACTIVE_AGENTS_SET_KEY = "metrics:active-agents";
    private static final long LATENCY_WINDOW_MAX_SIZE = 10_000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final long redisTtlSeconds;
    private final Counter redisWriteCounter;
    private final Counter redisErrorCounter;

    public RedisMetricsStore(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${neuralops.metrics.redis-ttl-seconds:86400}") long redisTtlSeconds,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.redisTtlSeconds = redisTtlSeconds;
        this.redisWriteCounter = Counter.builder("neuralops.redis.writes")
                .description("Redis metric write operations")
                .register(meterRegistry);
        this.redisErrorCounter = Counter.builder("neuralops.redis.errors")
                .description("Redis metric write failures")
                .register(meterRegistry);
    }

    public void recordTraceEvent(String agentId, String traceId, long latencyMs,
                                 Integer tokenCount, BigDecimal costUsd, boolean isError) {
        try {
            String statsKey = STATS_KEY_PREFIX + agentId;
            String latencyKey = LATENCY_SORTED_KEY_PREFIX + agentId;
            String lastSeenKey = LAST_SEEN_KEY_PREFIX + agentId;

            redisTemplate.executePipelined((connection) -> {
                byte[] statsKeyBytes = statsKey.getBytes();
                connection.hashCommands().hIncrBy(statsKeyBytes, "trace_count".getBytes(), 1);
                if (tokenCount != null) {
                    connection.hashCommands().hIncrBy(statsKeyBytes, "token_count".getBytes(), tokenCount);
                }
                if (isError) {
                    connection.hashCommands().hIncrBy(statsKeyBytes, "error_count".getBytes(), 1);
                }

                long now = System.currentTimeMillis();
                String member = traceId + ":" + now;
                connection.zSetCommands().zAdd(
                        latencyKey.getBytes(),
                        (double) latencyMs,
                        member.getBytes()
                );

                connection.stringCommands().set(
                        lastSeenKey.getBytes(),
                        String.valueOf(now).getBytes()
                );

                byte[] overviewKeyBytes = OVERVIEW_KEY.getBytes();
                connection.hashCommands().hIncrBy(overviewKeyBytes, "total_traces_today".getBytes(), 1);

                return null;
            });

            trimLatencyWindow(agentId);
            updateCostAccumulator(agentId, costUsd);

            redisWriteCounter.increment();
        } catch (Exception ex) {
            log.error("Failed to record trace event metrics in Redis for agentId={}: {}", agentId, ex.getMessage());
            redisErrorCounter.increment();
        }
    }

    public Map<String, Double> getLatencyPercentiles(String agentId) {
        String latencyKey = LATENCY_SORTED_KEY_PREFIX + agentId;
        Long count = redisTemplate.opsForZSet().size(latencyKey);
        if (count == null || count == 0) {
            return Map.of("p50", 0.0, "p95", 0.0, "p99", 0.0);
        }

        long p50Index = Math.max(0, (long) (count * 0.50) - 1);
        long p95Index = Math.max(0, (long) (count * 0.95) - 1);
        long p99Index = Math.max(0, (long) (count * 0.99) - 1);

        Set<Object> p50Set = redisTemplate.opsForZSet().range(latencyKey, p50Index, p50Index);
        Set<Object> p95Set = redisTemplate.opsForZSet().range(latencyKey, p95Index, p95Index);
        Set<Object> p99Set = redisTemplate.opsForZSet().range(latencyKey, p99Index, p99Index);

        Double p50Score = getScoreForMember(latencyKey, p50Set);
        Double p95Score = getScoreForMember(latencyKey, p95Set);
        Double p99Score = getScoreForMember(latencyKey, p99Set);

        Map<String, Double> result = new HashMap<>();
        result.put("p50", p50Score != null ? p50Score : 0.0);
        result.put("p95", p95Score != null ? p95Score : 0.0);
        result.put("p99", p99Score != null ? p99Score : 0.0);
        result.put("count", (double) count);
        return result;
    }

    public Map<String, Object> getAgentStats(String agentId) {
        String statsKey = STATS_KEY_PREFIX + agentId;
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(statsKey);
        Map<String, Object> stats = new HashMap<>();
        raw.forEach((k, v) -> stats.put(k.toString(), v));
        return stats;
    }

    public Map<String, Object> getOverview() {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(OVERVIEW_KEY);
        Map<String, Object> overview = new HashMap<>();
        raw.forEach((k, v) -> overview.put(k.toString(), v));
        Long activeAgents = redisTemplate.opsForSet().size(ACTIVE_AGENTS_SET_KEY);
        overview.put("active_agents", activeAgents != null ? activeAgents : 0L);
        return overview;
    }

    public void markAgentActive(String agentId) {
        redisTemplate.opsForSet().add(ACTIVE_AGENTS_SET_KEY, agentId);
        redisTemplate.expire(ACTIVE_AGENTS_SET_KEY, Duration.ofSeconds(redisTtlSeconds));
        redisTemplate.expire(STATS_KEY_PREFIX + agentId, Duration.ofSeconds(redisTtlSeconds));
        redisTemplate.expire(LATENCY_SORTED_KEY_PREFIX + agentId, Duration.ofSeconds(redisTtlSeconds));
    }

    private void trimLatencyWindow(String agentId) {
        String latencyKey = LATENCY_SORTED_KEY_PREFIX + agentId;
        Long size = redisTemplate.opsForZSet().size(latencyKey);
        if (size != null && size > LATENCY_WINDOW_MAX_SIZE) {
            redisTemplate.opsForZSet().removeRange(latencyKey, 0, size - LATENCY_WINDOW_MAX_SIZE - 1);
        }
    }

    private void updateCostAccumulator(String agentId, BigDecimal costUsd) {
        if (costUsd == null) return;
        String statsKey = STATS_KEY_PREFIX + agentId;
        Object current = redisTemplate.opsForHash().get(statsKey, "cost_usd_total");
        double currentCost = current != null ? Double.parseDouble(current.toString()) : 0.0;
        double newCost = currentCost + costUsd.doubleValue();
        redisTemplate.opsForHash().put(statsKey, "cost_usd_total", String.valueOf(newCost));
    }

    private Double getScoreForMember(String key, Set<Object> members) {
        if (members == null || members.isEmpty()) return null;
        Object member = members.iterator().next();
        return redisTemplate.opsForZSet().score(key, member);
    }
}
