package com.neuralops.metrics.service;

import com.neuralops.metrics.api.dto.AgentLatencyResponse;
import com.neuralops.metrics.api.dto.AgentStatsResponse;
import com.neuralops.metrics.api.dto.OverviewResponse;
import com.neuralops.metrics.domain.repository.AgentMetricRepository;
import com.neuralops.metrics.redis.RedisMetricsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsQueryServiceTest {

    @Mock
    private RedisMetricsStore redisMetricsStore;

    @Mock
    private AgentMetricRepository agentMetricRepository;

    @InjectMocks
    private MetricsQueryService metricsQueryService;

    private static final String AGENT_ID = "agent-test-001";

    @BeforeEach
    void setUp() {
    }

    @Test
    void getLatencyPercentiles_returnsMappedPercentiles() {
        when(redisMetricsStore.getLatencyPercentiles(AGENT_ID)).thenReturn(
                Map.of("p50", 120.0, "p95", 450.0, "p99", 980.0, "count", 500.0)
        );

        AgentLatencyResponse response = metricsQueryService.getLatencyPercentiles(AGENT_ID);

        assertThat(response.agentId()).isEqualTo(AGENT_ID);
        assertThat(response.p50Ms()).isEqualTo(120.0);
        assertThat(response.p95Ms()).isEqualTo(450.0);
        assertThat(response.p99Ms()).isEqualTo(980.0);
        assertThat(response.sampleCount()).isEqualTo(500L);
    }

    @Test
    void getLatencyPercentiles_returnsZeroDefaults_whenRedisEmpty() {
        when(redisMetricsStore.getLatencyPercentiles(AGENT_ID)).thenReturn(Collections.emptyMap());

        AgentLatencyResponse response = metricsQueryService.getLatencyPercentiles(AGENT_ID);

        assertThat(response.p50Ms()).isZero();
        assertThat(response.p95Ms()).isZero();
        assertThat(response.p99Ms()).isZero();
        assertThat(response.sampleCount()).isZero();
    }

    @Test
    void getAgentStats_computesErrorRateCorrectly() {
        when(redisMetricsStore.getAgentStats(AGENT_ID)).thenReturn(Map.of(
                "trace_count", "200",
                "token_count", "50000",
                "error_count", "10",
                "cost_usd_total", "0.12345678"
        ));

        AgentStatsResponse response = metricsQueryService.getAgentStats(AGENT_ID);

        assertThat(response.agentId()).isEqualTo(AGENT_ID);
        assertThat(response.traceCount()).isEqualTo(200L);
        assertThat(response.tokenCount()).isEqualTo(50000L);
        assertThat(response.errorCount()).isEqualTo(10L);
        assertThat(response.errorRatePct()).isEqualTo(5.0);
        assertThat(response.costUsdTotal()).isEqualByComparingTo(new BigDecimal("0.12345678"));
    }

    @Test
    void getAgentStats_returnsZeroErrorRate_whenNoTraces() {
        when(redisMetricsStore.getAgentStats(AGENT_ID)).thenReturn(Map.of(
                "trace_count", "0",
                "token_count", "0",
                "error_count", "0",
                "cost_usd_total", "0"
        ));

        AgentStatsResponse response = metricsQueryService.getAgentStats(AGENT_ID);

        assertThat(response.errorRatePct()).isZero();
    }

    @Test
    void getAgentStats_handlesGracefullyOnMalformedRedisValues() {
        when(redisMetricsStore.getAgentStats(AGENT_ID)).thenReturn(Map.of(
                "trace_count", "not-a-number"
        ));

        AgentStatsResponse response = metricsQueryService.getAgentStats(AGENT_ID);

        assertThat(response.traceCount()).isZero();
        assertThat(response.errorRatePct()).isZero();
    }

    @Test
    void getOverview_returnsMappedActiveAgentsAndTraces() {
        when(redisMetricsStore.getOverview()).thenReturn(Map.of(
                "active_agents", "42",
                "total_traces_today", "15000"
        ));

        OverviewResponse response = metricsQueryService.getOverview();

        assertThat(response.activeAgents()).isEqualTo(42L);
        assertThat(response.totalTracesToday()).isEqualTo(15000L);
    }

    @Test
    void getOverview_returnsZeros_whenRedisEmpty() {
        when(redisMetricsStore.getOverview()).thenReturn(Collections.emptyMap());

        OverviewResponse response = metricsQueryService.getOverview();

        assertThat(response.activeAgents()).isZero();
        assertThat(response.totalTracesToday()).isZero();
    }
}
