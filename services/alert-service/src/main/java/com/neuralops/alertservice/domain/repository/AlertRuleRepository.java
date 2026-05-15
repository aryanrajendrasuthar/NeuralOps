package com.neuralops.alertservice.domain.repository;

import com.neuralops.alertservice.domain.entity.AlertRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRuleEntity, Long> {

    @Query("""
            SELECT r FROM AlertRuleEntity r
            WHERE r.isEnabled = TRUE
              AND (r.agentId IS NULL OR r.agentId = :agentId)
            """)
    List<AlertRuleEntity> findEnabledRulesForAgent(@Param("agentId") String agentId);

    List<AlertRuleEntity> findByAgentIdAndIsEnabledTrue(String agentId);

    List<AlertRuleEntity> findAllByIsEnabledTrue();
}
