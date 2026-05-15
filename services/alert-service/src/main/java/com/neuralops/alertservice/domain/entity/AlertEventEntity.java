package com.neuralops.alertservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "alert_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventEntity {

    public enum WebhookStatus { PENDING, DELIVERED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private AlertRuleEntity rule;

    @Column(name = "agent_id", nullable = false, length = 255)
    private String agentId;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "metric_value", nullable = false)
    private Double metricValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "webhook_status", nullable = false, length = 20)
    private WebhookStatus webhookStatus;

    @Column(name = "webhook_attempts", nullable = false)
    private Integer webhookAttempts;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;
}
