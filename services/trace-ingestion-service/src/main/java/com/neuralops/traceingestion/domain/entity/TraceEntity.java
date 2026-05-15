package com.neuralops.traceingestion.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "traces",
    indexes = {
        @Index(name = "idx_traces_agent_id_created_at", columnList = "agent_id, ingested_at"),
        @Index(name = "idx_traces_session_id", columnList = "session_id"),
        @Index(name = "idx_traces_trace_type", columnList = "trace_type"),
        @Index(name = "idx_traces_event_timestamp", columnList = "event_timestamp")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "trace_id", unique = true, nullable = false, length = 36)
    private String traceId;

    @Column(name = "agent_id", nullable = false, length = 255)
    private String agentId;

    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    @Column(name = "trace_type", nullable = false, length = 50)
    private String traceType;

    @Column(name = "latency_ms", nullable = false)
    private Long latencyMs;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "estimated_cost_usd", precision = 12, scale = 8)
    private BigDecimal estimatedCostUsd;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;
}
