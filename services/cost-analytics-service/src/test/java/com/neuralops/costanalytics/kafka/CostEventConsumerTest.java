package com.neuralops.costanalytics.kafka;

import com.neuralops.costanalytics.domain.repository.AgentDailyCostRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CostEventConsumerTest {

    @Mock
    private AgentDailyCostRepository agentDailyCostRepository;

    @Mock
    private Acknowledgment acknowledgment;

    private CostEventConsumer consumer;

    private static final String AGENT_ID = "agent-cost-001";

    @BeforeEach
    void setUp() {
        consumer = new CostEventConsumer(
                agentDailyCostRepository,
                new BigDecimal("0.002"),
                new SimpleMeterRegistry()
        );
    }

    private ConsumerRecord<String, Map<String, Object>> buildRecord(Map<String, Object> payload) {
        return new ConsumerRecord<>("neuralops.traces.raw", 0, 0L, AGENT_ID, payload);
    }

    @Test
    void consume_usesEstimatedCostUsd_whenPresent() {
        Map<String, Object> event = new HashMap<>();
        event.put("agentId", AGENT_ID);
        event.put("estimatedCostUsd", 0.0050);
        event.put("tokenCount", 2500);
        event.put("eventTimestamp", Instant.now().toString());

        consumer.consume(buildRecord(event), acknowledgment);

        ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(agentDailyCostRepository).upsertDailyCost(
                eq(AGENT_ID), any(), costCaptor.capture(), eq(1L), eq(2500L));
        assertThat(costCaptor.getValue().doubleValue()).isEqualTo(0.005);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_derivesTokenBasedCost_whenNoEstimatedCostUsd() {
        Map<String, Object> event = new HashMap<>();
        event.put("agentId", AGENT_ID);
        event.put("tokenCount", 1000);
        event.put("eventTimestamp", Instant.now().toString());

        consumer.consume(buildRecord(event), acknowledgment);

        ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(agentDailyCostRepository).upsertDailyCost(
                eq(AGENT_ID), any(), costCaptor.capture(), eq(1L), eq(1000L));

        assertThat(costCaptor.getValue()).isEqualByComparingTo(new BigDecimal("0.002"));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_recordsZeroCost_whenNoTokensAndNoCost() {
        Map<String, Object> event = new HashMap<>();
        event.put("agentId", AGENT_ID);
        event.put("eventTimestamp", Instant.now().toString());

        consumer.consume(buildRecord(event), acknowledgment);

        verify(agentDailyCostRepository).upsertDailyCost(
                eq(AGENT_ID), any(), eq(BigDecimal.ZERO), eq(1L), eq(0L));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_defaultsAgentId_whenMissing() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventTimestamp", Instant.now().toString());

        consumer.consume(buildRecord(event), acknowledgment);

        verify(agentDailyCostRepository).upsertDailyCost(
                eq("unknown"), any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_stillAcknowledges_onRepositoryException() {
        Map<String, Object> event = new HashMap<>();
        event.put("agentId", AGENT_ID);
        event.put("estimatedCostUsd", 0.01);

        org.mockito.Mockito.doThrow(new RuntimeException("DB down"))
                .when(agentDailyCostRepository).upsertDailyCost(any(), any(), any(), any(), any());

        consumer.consume(buildRecord(event), acknowledgment);

        verify(acknowledgment).acknowledge();
    }
}
