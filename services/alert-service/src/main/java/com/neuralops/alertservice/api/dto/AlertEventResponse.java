package com.neuralops.alertservice.api.dto;

import com.neuralops.alertservice.domain.entity.AlertEventEntity.WebhookStatus;
import com.neuralops.alertservice.domain.entity.AlertRuleEntity.AlertMetric;

import java.time.Instant;

public record AlertEventResponse(
        Long id,
        Long ruleId,
        String agentId,
        AlertMetric metric,
        Double metricValue,
        Double threshold,
        Instant triggeredAt,
        WebhookStatus webhookStatus,
        Integer webhookAttempts
) {}
