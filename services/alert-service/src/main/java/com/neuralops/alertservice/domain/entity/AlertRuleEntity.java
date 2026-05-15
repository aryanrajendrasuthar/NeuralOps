package com.neuralops.alertservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleEntity {

    public enum AlertMetric { LATENCY_P99, LATENCY_P95, ERROR_RATE, ANOMALY_SCORE }
    public enum AlertOperator { GT, LT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", length = 255)
    private String agentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 50)
    private AlertMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 10)
    private AlertOperator operator;

    @Column(name = "threshold", nullable = false)
    private Double threshold;

    @Column(name = "webhook_url", nullable = false)
    private String webhookUrl;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
