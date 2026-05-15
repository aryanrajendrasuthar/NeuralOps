package com.neuralops.costanalytics.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "agent_daily_cost")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDailyCostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false, length = 255)
    private String agentId;

    @Column(name = "cost_date", nullable = false)
    private LocalDate costDate;

    @Column(name = "total_cost_usd", nullable = false, precision = 14, scale = 8)
    private BigDecimal totalCostUsd;

    @Column(name = "trace_count", nullable = false)
    private Long traceCount;

    @Column(name = "token_count", nullable = false)
    private Long tokenCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
