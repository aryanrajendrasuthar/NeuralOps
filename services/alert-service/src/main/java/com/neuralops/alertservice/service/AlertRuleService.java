package com.neuralops.alertservice.service;

import com.neuralops.alertservice.api.dto.AlertEventResponse;
import com.neuralops.alertservice.api.dto.AlertRuleRequest;
import com.neuralops.alertservice.api.dto.AlertRuleResponse;
import com.neuralops.alertservice.domain.entity.AlertEventEntity;
import com.neuralops.alertservice.domain.entity.AlertRuleEntity;
import com.neuralops.alertservice.domain.repository.AlertEventRepository;
import com.neuralops.alertservice.domain.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;

    @Transactional
    public AlertRuleResponse createRule(AlertRuleRequest request) {
        Instant now = Instant.now();
        AlertRuleEntity entity = AlertRuleEntity.builder()
                .agentId(request.agentId())
                .metric(request.metric())
                .operator(request.operator())
                .threshold(request.threshold())
                .webhookUrl(request.webhookUrl())
                .isEnabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return toResponse(alertRuleRepository.save(entity));
    }

    public AlertRuleResponse getRule(Long id) {
        return alertRuleRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Alert rule not found: " + id));
    }

    public List<AlertRuleResponse> listRules() {
        return alertRuleRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public AlertRuleResponse updateRule(Long id, AlertRuleRequest request) {
        AlertRuleEntity entity = alertRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert rule not found: " + id));
        entity.setAgentId(request.agentId());
        entity.setMetric(request.metric());
        entity.setOperator(request.operator());
        entity.setThreshold(request.threshold());
        entity.setWebhookUrl(request.webhookUrl());
        entity.setUpdatedAt(Instant.now());
        return toResponse(alertRuleRepository.save(entity));
    }

    @Transactional
    public void deleteRule(Long id) {
        if (!alertRuleRepository.existsById(id)) {
            throw new IllegalArgumentException("Alert rule not found: " + id);
        }
        alertRuleRepository.deleteById(id);
    }

    @Transactional
    public AlertRuleResponse setEnabled(Long id, boolean enabled) {
        AlertRuleEntity entity = alertRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert rule not found: " + id));
        entity.setIsEnabled(enabled);
        entity.setUpdatedAt(Instant.now());
        return toResponse(alertRuleRepository.save(entity));
    }

    public Page<AlertEventResponse> getAlertHistory(String agentId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("triggeredAt").descending());
        return alertEventRepository.findByAgentIdOrderByTriggeredAtDesc(agentId, pageable)
                .map(this::toEventResponse);
    }

    private AlertRuleResponse toResponse(AlertRuleEntity e) {
        return new AlertRuleResponse(e.getId(), e.getAgentId(), e.getMetric(), e.getOperator(),
                e.getThreshold(), e.getWebhookUrl(), e.getIsEnabled(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private AlertEventResponse toEventResponse(AlertEventEntity e) {
        return new AlertEventResponse(e.getId(), e.getRule().getId(), e.getAgentId(),
                e.getRule().getMetric(), e.getMetricValue(), e.getRule().getThreshold(),
                e.getTriggeredAt(), e.getWebhookStatus(), e.getWebhookAttempts());
    }
}
