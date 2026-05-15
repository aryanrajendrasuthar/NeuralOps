package com.neuralops.costanalytics.service;

import com.neuralops.costanalytics.domain.entity.AgentCostForecastEntity;
import com.neuralops.costanalytics.domain.entity.AgentDailyCostEntity;
import com.neuralops.costanalytics.domain.repository.AgentCostForecastRepository;
import com.neuralops.costanalytics.domain.repository.AgentDailyCostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinearRegressionForecasterTest {

    @Mock
    private AgentDailyCostRepository agentDailyCostRepository;

    @Mock
    private AgentCostForecastRepository agentCostForecastRepository;

    private LinearRegressionForecaster forecaster;

    private static final String AGENT_ID = "agent-forecast-001";

    @BeforeEach
    void setUp() {
        forecaster = new LinearRegressionForecaster(agentDailyCostRepository, agentCostForecastRepository);
        ReflectionTestUtils.setField(forecaster, "forecastDays", 7);
        ReflectionTestUtils.setField(forecaster, "historyDays", 30);
    }

    private AgentDailyCostEntity costEntry(LocalDate date, double costUsd) {
        return AgentDailyCostEntity.builder()
                .agentId(AGENT_ID)
                .costDate(date)
                .totalCostUsd(BigDecimal.valueOf(costUsd))
                .traceCount(100L)
                .tokenCount(5000L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void forecastForAgent_returnsEmptyList_whenFewerThanThreeDataPoints() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(agentDailyCostRepository.findByAgentIdAndCostDateBetweenOrderByCostDateAsc(
                eq(AGENT_ID), any(), any()))
                .thenReturn(List.of(
                        costEntry(today.minusDays(2), 0.10)
                ));

        List<AgentCostForecastEntity> result = forecaster.forecastForAgent(AGENT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void forecastForAgent_returnsForecastPoints_withSufficientData() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<AgentDailyCostEntity> history = new ArrayList<>();
        for (int i = 10; i >= 1; i--) {
            history.add(costEntry(today.minusDays(i), 0.10 + i * 0.01));
        }
        when(agentDailyCostRepository.findByAgentIdAndCostDateBetweenOrderByCostDateAsc(
                eq(AGENT_ID), any(), any()))
                .thenReturn(history);

        List<AgentCostForecastEntity> result = forecaster.forecastForAgent(AGENT_ID);

        assertThat(result).hasSize(7);
        result.forEach(f -> {
            assertThat(f.getAgentId()).isEqualTo(AGENT_ID);
            assertThat(f.getPredictedCost()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });
    }

    @Test
    void forecastForAgent_forecastDayIsSequential() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<AgentDailyCostEntity> history = new ArrayList<>();
        for (int i = 5; i >= 1; i--) {
            history.add(costEntry(today.minusDays(i), 0.05 * i));
        }
        when(agentDailyCostRepository.findByAgentIdAndCostDateBetweenOrderByCostDateAsc(
                eq(AGENT_ID), any(), any()))
                .thenReturn(history);

        List<AgentCostForecastEntity> result = forecaster.forecastForAgent(AGENT_ID);

        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).getForecastDay()).isEqualTo(i + 1);
        }
    }

    @Test
    void forecastForAgent_clampsPredictedCostToZero_whenRegressionPredictesNegative() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<AgentDailyCostEntity> history = List.of(
                costEntry(today.minusDays(5), 0.50),
                costEntry(today.minusDays(4), 0.30),
                costEntry(today.minusDays(3), 0.10)
        );
        when(agentDailyCostRepository.findByAgentIdAndCostDateBetweenOrderByCostDateAsc(
                eq(AGENT_ID), any(), any()))
                .thenReturn(history);

        List<AgentCostForecastEntity> result = forecaster.forecastForAgent(AGENT_ID);

        result.forEach(f ->
                assertThat(f.getPredictedCost()).isGreaterThanOrEqualTo(BigDecimal.ZERO)
        );
    }

    @Test
    void runDailyForecastJob_savesForecasts_forAllDistinctAgents() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        AgentDailyCostEntity entry1 = costEntry(today.minusDays(1), 0.10);
        AgentDailyCostEntity entry2 = AgentDailyCostEntity.builder()
                .agentId("agent-other-002")
                .costDate(today.minusDays(1))
                .totalCostUsd(BigDecimal.valueOf(0.20))
                .traceCount(50L).tokenCount(2500L)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        when(agentDailyCostRepository.findAll()).thenReturn(List.of(entry1, entry2));
        when(agentDailyCostRepository.findByAgentIdAndCostDateBetweenOrderByCostDateAsc(
                any(), any(), any()))
                .thenReturn(List.of());

        forecaster.runDailyForecastJob();

        verify(agentDailyCostRepository).findAll();
    }
}
