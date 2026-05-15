package com.neuralops.traceingestion.domain.repository;

import com.neuralops.traceingestion.domain.entity.AgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<AgentEntity, UUID> {

    Optional<AgentEntity> findByAgentId(String agentId);
}
