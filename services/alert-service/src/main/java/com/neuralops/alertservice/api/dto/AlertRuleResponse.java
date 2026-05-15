package com.neuralops.alertservice.api.dto;

import com.neuralops.alertservice.domain.entity.AlertRuleEntity.AlertMetric;
import com.neuralops.alertservice.domain.entity.AlertRuleEntity.AlertOperator;

import java.time.Instant;

public record AlertRuleResponse(
        Long id,
        String agentId,
        AlertMetric metric,
        AlertOperator operator,
        Double threshold,
        String webhookUrl,
        Boolean isEnabled,
        Instant createdAt,
        Instant updatedAt
) {}
