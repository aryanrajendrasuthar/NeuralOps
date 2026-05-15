package com.neuralops.traceingestion.domain.repository;

import com.neuralops.traceingestion.domain.entity.TraceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TraceRepository extends JpaRepository<TraceEntity, UUID> {

    Optional<TraceEntity> findByTraceId(String traceId);

    Page<TraceEntity> findByAgentIdOrderByIngestedAtDesc(String agentId, Pageable pageable);

    Page<TraceEntity> findBySessionIdOrderByEventTimestampAsc(String sessionId, Pageable pageable);

    @Query("SELECT COUNT(t) FROM TraceEntity t WHERE t.agentId = :agentId " +
           "AND t.traceType = 'ERROR' AND t.ingestedAt >= :since")
    long countErrorsByAgentIdSince(@Param("agentId") String agentId, @Param("since") Instant since);

    @Query("SELECT COUNT(t) FROM TraceEntity t WHERE t.agentId = :agentId AND t.ingestedAt >= :since")
    long countByAgentIdSince(@Param("agentId") String agentId, @Param("since") Instant since);
}
