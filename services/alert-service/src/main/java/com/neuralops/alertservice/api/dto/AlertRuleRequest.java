package com.neuralops.alertservice.api.dto;

import com.neuralops.alertservice.domain.entity.AlertRuleEntity.AlertMetric;
import com.neuralops.alertservice.domain.entity.AlertRuleEntity.AlertOperator;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;

public record AlertRuleRequest(
        String agentId,

        @NotNull
        AlertMetric metric,

        @NotNull
        AlertOperator operator,

        @NotNull
        Double threshold,

        @NotNull
        @URL
        String webhookUrl
) {}
