package com.neuralops.alertservice.service;

import com.neuralops.alertservice.domain.entity.AlertEventEntity;
import com.neuralops.alertservice.domain.entity.AlertEventEntity.WebhookStatus;
import com.neuralops.alertservice.domain.entity.AlertRuleEntity;
import com.neuralops.alertservice.domain.entity.AlertRuleEntity.AlertMetric;
import com.neuralops.alertservice.domain.entity.AlertRuleEntity.AlertOperator;
import com.neuralops.alertservice.domain.repository.AlertEventRepository;
import com.neuralops.alertservice.domain.repository.AlertRuleRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleEngineTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;
    @Mock
    private AlertEventRepository alertEventRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private WebhookDeliveryService webhookDeliveryService;

    private AlertRuleEngine alertRuleEngine;

    private static final String AGENT_ID = "agent-test-001";

    @BeforeEach
    void setUp() {
        alertRuleEngine = new AlertRuleEngine(
                alertRuleRepository,
                alertEventRepository,
                redisTemplate,
                webhookDeliveryService,
                300L,
                new SimpleMeterRegistry()
        );
    }

    private AlertRuleEntity buildRule(AlertMetric metric, AlertOperator operator, double threshold) {
        return AlertRuleEntity.builder()
                .id(1L)
                .agentId(AGENT_ID)
                .metric(metric)
                .operator(operator)
                .threshold(threshold)
                .webhookUrl("https://hooks.example.com/alert")
                .isEnabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void evaluateTraceEvent_firesAlert_whenLatencyExceedsThreshold() {
        AlertRuleEntity rule = buildRule(AlertMetric.LATENCY_P99, AlertOperator.GT, 2000.0);
        when(alertRuleRepository.findEnabledRulesForAgent(AGENT_ID)).thenReturn(List.of(rule));
        when(redisTemplate.hasKey(any())).thenReturn(false);
        AlertEventEntity savedEvent = AlertEventEntity.builder().id(10L).rule(rule)
                .agentId(AGENT_ID).triggeredAt(Instant.now())
                .metricValue(3000.0).webhookStatus(WebhookStatus.PENDING).webhookAttempts(0).build();
        when(alertEventRepository.save(any())).thenReturn(savedEvent);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        alertRuleEngine.evaluateTraceEvent(Map.of("agentId", AGENT_ID, "latencyMs", 3000, "traceType", "LLM_CALL"));

        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(alertEventRepository).save(captor.capture());
        assertThat(captor.getValue().getMetricValue()).isEqualTo(3000.0);
        assertThat(captor.getValue().getWebhookStatus()).isEqualTo(WebhookStatus.PENDING);
        verify(webhookDeliveryService).deliverAsync(savedEvent);
    }

    @Test
    void evaluateTraceEvent_doesNotFireAlert_whenLatencyBelowThreshold() {
        AlertRuleEntity rule = buildRule(AlertMetric.LATENCY_P99, AlertOperator.GT, 2000.0);
        when(alertRuleRepository.findEnabledRulesForAgent(AGENT_ID)).thenReturn(List.of(rule));

        alertRuleEngine.evaluateTraceEvent(Map.of("agentId", AGENT_ID, "latencyMs", 500, "traceType", "LLM_CALL"));

        verify(alertEventRepository, never()).save(any());
        verify(webhookDeliveryService, never()).deliverAsync(any());
    }

    @Test
    void evaluateTraceEvent_suppressed_byDeduplicationWindow() {
        AlertRuleEntity rule = buildRule(AlertMetric.LATENCY_P99, AlertOperator.GT, 2000.0);
        when(alertRuleRepository.findEnabledRulesForAgent(AGENT_ID)).thenReturn(List.of(rule));
        when(redisTemplate.hasKey(any())).thenReturn(true);

        alertRuleEngine.evaluateTraceEvent(Map.of("agentId", AGENT_ID, "latencyMs", 5000, "traceType", "LLM_CALL"));

        verify(alertEventRepository, never()).save(any());
        verify(webhookDeliveryService, never()).deliverAsync(any());
    }

    @Test
    void evaluateTraceEvent_skipped_whenAgentIdMissing() {
        alertRuleEngine.evaluateTraceEvent(Map.of("latencyMs", 9999, "traceType", "LLM_CALL"));

        verify(alertRuleRepository, never()).findEnabledRulesForAgent(any());
    }

    @Test
    void evaluateAnomalyEvent_firesAlert_whenScoreBelowThreshold() {
        AlertRuleEntity rule = buildRule(AlertMetric.ANOMALY_SCORE, AlertOperator.LT, -0.1);
        when(alertRuleRepository.findEnabledRulesForAgent(AGENT_ID)).thenReturn(List.of(rule));
        when(redisTemplate.hasKey(any())).thenReturn(false);
        AlertEventEntity savedEvent = AlertEventEntity.builder().id(11L).rule(rule)
                .agentId(AGENT_ID).triggeredAt(Instant.now())
                .metricValue(-0.5).webhookStatus(WebhookStatus.PENDING).webhookAttempts(0).build();
        when(alertEventRepository.save(any())).thenReturn(savedEvent);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        alertRuleEngine.evaluateAnomalyEvent(Map.of("agent_id", AGENT_ID, "anomaly_score", -0.5));

        verify(alertEventRepository).save(any());
        verify(webhookDeliveryService).deliverAsync(savedEvent);
    }

    @Test
    void evaluateAnomalyEvent_skipped_whenAnomalyScoreNull() {
        alertRuleEngine.evaluateAnomalyEvent(Map.of("agent_id", AGENT_ID));

        verify(alertRuleRepository, never()).findEnabledRulesForAgent(any());
    }

    @Test
    void evaluateTraceEvent_firesErrorAlert_onErrorTraceType() {
        AlertRuleEntity rule = buildRule(AlertMetric.ERROR_RATE, AlertOperator.GT, 0.0);
        when(alertRuleRepository.findEnabledRulesForAgent(AGENT_ID)).thenReturn(List.of(rule));
        when(redisTemplate.hasKey(any())).thenReturn(false);
        AlertEventEntity saved = AlertEventEntity.builder().id(12L).rule(rule)
                .agentId(AGENT_ID).triggeredAt(Instant.now())
                .metricValue(1.0).webhookStatus(WebhookStatus.PENDING).webhookAttempts(0).build();
        when(alertEventRepository.save(any())).thenReturn(saved);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        alertRuleEngine.evaluateTraceEvent(Map.of("agentId", AGENT_ID, "traceType", "ERROR"));

        verify(alertEventRepository).save(any());
    }

    @Test
    void evaluateTraceEvent_usesCorrectDedupKeyFormat() {
        AlertRuleEntity rule = buildRule(AlertMetric.LATENCY_P99, AlertOperator.GT, 1000.0);
        when(alertRuleRepository.findEnabledRulesForAgent(AGENT_ID)).thenReturn(List.of(rule));
        when(redisTemplate.hasKey(any())).thenReturn(false);
        when(alertEventRepository.save(any())).thenReturn(AlertEventEntity.builder().id(1L).rule(rule)
                .agentId(AGENT_ID).triggeredAt(Instant.now()).metricValue(2000.0)
                .webhookStatus(WebhookStatus.PENDING).webhookAttempts(0).build());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        alertRuleEngine.evaluateTraceEvent(Map.of("agentId", AGENT_ID, "latencyMs", 2000, "traceType", "LLM_CALL"));

        verify(redisTemplate).hasKey(eq("alert:dedup:1:" + AGENT_ID));
    }
}
