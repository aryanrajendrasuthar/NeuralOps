package com.neuralops.metrics.domain.repository;

import com.neuralops.metrics.domain.entity.AgentMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AgentMetricRepository extends JpaRepository<AgentMetricEntity, AgentMetricEntity.AgentMetricId> {

    @Query(value = """
            SELECT
                PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY latency_ms) AS p50,
                PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms) AS p95,
                PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY latency_ms) AS p99
            FROM agent_metrics
            WHERE agent_id = :agentId
              AND time >= :since
            """, nativeQuery = true)
    Object[] findLatencyPercentilesForAgent(
            @Param("agentId") String agentId,
            @Param("since") Instant since
    );

    @Query(value = """
            SELECT COUNT(*) AS total, SUM(CASE WHEN is_error THEN 1 ELSE 0 END) AS errors
            FROM agent_metrics
            WHERE agent_id = :agentId
              AND time >= :since
            """, nativeQuery = true)
    Object[] findErrorRateForAgent(
            @Param("agentId") String agentId,
            @Param("since") Instant since
    );

    @Query(value = """
            SELECT bucket, p95_latency_ms, p99_latency_ms, error_count, total_cost_usd
            FROM agent_metrics_hourly
            WHERE agent_id = :agentId
              AND bucket >= :since
            ORDER BY bucket ASC
            """, nativeQuery = true)
    List<Object[]> findHourlyMetricsForAgent(
            @Param("agentId") String agentId,
            @Param("since") Instant since
    );
}
