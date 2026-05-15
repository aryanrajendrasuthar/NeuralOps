package com.neuralops.metrics.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "agent_metrics")
@IdClass(AgentMetricEntity.AgentMetricId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMetricEntity {

    @Id
    @Column(name = "time", nullable = false)
    private Instant time;

    @Id
    @Column(name = "agent_id", nullable = false, length = 255)
    private String agentId;

    @Column(name = "trace_type", nullable = false, length = 50)
    private String traceType;

    @Column(name = "latency_ms", nullable = false)
    private Long latencyMs;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "cost_usd", precision = 12, scale = 8)
    private BigDecimal costUsd;

    @Column(name = "is_error", nullable = false)
    private Boolean isError;

    public static class AgentMetricId implements Serializable {
        private Instant time;
        private String agentId;
    }
}
