package com.neuralops.costanalytics.domain.repository;

import com.neuralops.costanalytics.domain.entity.AgentCostForecastEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AgentCostForecastRepository extends JpaRepository<AgentCostForecastEntity, Long> {

    @Query("""
            SELECT f FROM AgentCostForecastEntity f
            WHERE f.agentId = :agentId
              AND f.forecastDate = (
                  SELECT MAX(f2.forecastDate)
                  FROM AgentCostForecastEntity f2
                  WHERE f2.agentId = :agentId
              )
            ORDER BY f.forecastDay ASC
            """)
    List<AgentCostForecastEntity> findLatestForecastForAgent(@Param("agentId") String agentId);

    void deleteByAgentIdAndForecastDate(String agentId, LocalDate forecastDate);
}
