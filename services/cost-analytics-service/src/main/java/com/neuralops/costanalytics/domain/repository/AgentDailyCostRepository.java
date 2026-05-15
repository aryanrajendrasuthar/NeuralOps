package com.neuralops.costanalytics.domain.repository;

import com.neuralops.costanalytics.domain.entity.AgentDailyCostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentDailyCostRepository extends JpaRepository<AgentDailyCostEntity, Long> {

    Optional<AgentDailyCostEntity> findByAgentIdAndCostDate(String agentId, LocalDate costDate);

    List<AgentDailyCostEntity> findByAgentIdAndCostDateBetweenOrderByCostDateAsc(
            String agentId, LocalDate from, LocalDate to);

    @Modifying
    @Query(value = """
            INSERT INTO agent_daily_cost
                (agent_id, cost_date, total_cost_usd, trace_count, token_count, created_at, updated_at)
            VALUES
                (:agentId, :costDate, :costUsd, :traceCount, :tokenCount, NOW(), NOW())
            ON CONFLICT (agent_id, cost_date) DO UPDATE SET
                total_cost_usd = agent_daily_cost.total_cost_usd + EXCLUDED.total_cost_usd,
                trace_count    = agent_daily_cost.trace_count + EXCLUDED.trace_count,
                token_count    = agent_daily_cost.token_count + EXCLUDED.token_count,
                updated_at     = NOW()
            """, nativeQuery = true)
    void upsertDailyCost(
            @Param("agentId") String agentId,
            @Param("costDate") LocalDate costDate,
            @Param("costUsd") BigDecimal costUsd,
            @Param("traceCount") long traceCount,
            @Param("tokenCount") long tokenCount
    );
}
