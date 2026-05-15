package com.neuralops.alertservice.service;

import com.neuralops.alertservice.domain.entity.AlertEventEntity;
import com.neuralops.alertservice.domain.entity.AlertEventEntity.WebhookStatus;
import com.neuralops.alertservice.domain.entity.AlertRuleEntity;
import com.neuralops.alertservice.domain.entity.AlertRuleEntity.AlertMetric;
import com.neuralops.alertservice.domain.entity.AlertRuleEntity.AlertOperator;
import com.neuralops.alertservice.domain.repository.AlertEventRepository;
import com.neuralops.alertservice.domain.repository.AlertRuleRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AlertRuleEngine {

    private static final String DEDUP_KEY_PREFIX = "alert:dedup:";

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final WebhookDeliveryService webhookDeliveryService;
    private final long dedupWindowSeconds;
    private final Counter alertsFiredCounter;
    private final Counter alertsDedupedCounter;

    public AlertRuleEngine(
            AlertRuleRepository alertRuleRepository,
            AlertEventRepository alertEventRepository,
            RedisTemplate<String, Object> redisTemplate,
            WebhookDeliveryService webhookDeliveryService,
            @Value("${neuralops.alerting.deduplication-window-seconds:300}") long dedupWindowSeconds,
            MeterRegistry meterRegistry) {
        this.alertRuleRepository = alertRuleRepository;
        this.alertEventRepository = alertEventRepository;
        this.redisTemplate = redisTemplate;
        this.webhookDeliveryService = webhookDeliveryService;
        this.dedupWindowSeconds = dedupWindowSeconds;
        this.alertsFiredCounter = Counter.builder("neuralops.alerts.fired")
                .description("Total alert events that passed rule evaluation and deduplication")
                .register(meterRegistry);
        this.alertsDedupedCounter = Counter.builder("neuralops.alerts.deduped")
                .description("Total alert events suppressed by the deduplication window")
                .register(meterRegistry);
    }

    @Transactional
    public void evaluateTraceEvent(Map<String, Object> event) {
        String agentId = extractString(event, "agentId");
        if (agentId == null) return;

        Number latencyMs = (Number) event.get("latencyMs");
        String traceType = extractString(event, "traceType");
        boolean isError = "ERROR".equals(traceType);

        List<AlertRuleEntity> rules = alertRuleRepository.findEnabledRulesForAgent(agentId);
        for (AlertRuleEntity rule : rules) {
            if (rule.getMetric() == AlertMetric.LATENCY_P99 || rule.getMetric() == AlertMetric.LATENCY_P95) {
                if (latencyMs == null) continue;
                double value = latencyMs.doubleValue();
                if (conditionMet(rule, value)) {
                    fireAlert(rule, agentId, value);
                }
            } else if (rule.getMetric() == AlertMetric.ERROR_RATE && isError) {
                fireAlert(rule, agentId, 1.0);
            }
        }
    }

    @Transactional
    public void evaluateAnomalyEvent(Map<String, Object> event) {
        String agentId = extractString(event, "agent_id");
        if (agentId == null) return;

        Number anomalyScore = (Number) event.get("anomaly_score");
        if (anomalyScore == null) return;

        double score = anomalyScore.doubleValue();
        List<AlertRuleEntity> rules = alertRuleRepository.findEnabledRulesForAgent(agentId);
        for (AlertRuleEntity rule : rules) {
            if (rule.getMetric() == AlertMetric.ANOMALY_SCORE && conditionMet(rule, score)) {
                fireAlert(rule, agentId, score);
            }
        }
    }

    private boolean conditionMet(AlertRuleEntity rule, double value) {
        return switch (rule.getOperator()) {
            case GT -> value > rule.getThreshold();
            case LT -> value < rule.getThreshold();
        };
    }

    private void fireAlert(AlertRuleEntity rule, String agentId, double metricValue) {
        String dedupKey = DEDUP_KEY_PREFIX + rule.getId() + ":" + agentId;
        Boolean alreadyFired = redisTemplate.hasKey(dedupKey);
        if (Boolean.TRUE.equals(alreadyFired)) {
            alertsDedupedCounter.increment();
            log.debug("Alert suppressed by deduplication: ruleId={} agentId={}", rule.getId(), agentId);
            return;
        }

        AlertEventEntity event = AlertEventEntity.builder()
                .rule(rule)
                .agentId(agentId)
                .triggeredAt(Instant.now())
                .metricValue(metricValue)
                .webhookStatus(WebhookStatus.PENDING)
                .webhookAttempts(0)
                .build();
        AlertEventEntity saved = alertEventRepository.save(event);

        redisTemplate.opsForValue().set(dedupKey, "1", Duration.ofSeconds(dedupWindowSeconds));

        alertsFiredCounter.increment();
        log.info("Alert fired: ruleId={} agentId={} metric={} value={} threshold={}",
                rule.getId(), agentId, rule.getMetric(), metricValue, rule.getThreshold());

        webhookDeliveryService.deliverAsync(saved);
    }

    private String extractString(Map<String, Object> event, String key) {
        Object value = event.get(key);
        return value != null ? value.toString() : null;
    }
}
