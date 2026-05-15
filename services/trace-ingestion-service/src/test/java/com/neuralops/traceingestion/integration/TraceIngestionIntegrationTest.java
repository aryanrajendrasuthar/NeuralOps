package com.neuralops.traceingestion.integration;

import com.neuralops.traceingestion.api.dto.TraceEventRequest;
import com.neuralops.traceingestion.api.dto.TraceEventResponse;
import com.neuralops.traceingestion.api.dto.TraceType;
import com.neuralops.traceingestion.domain.repository.AgentRepository;
import com.neuralops.traceingestion.domain.repository.SessionRepository;
import com.neuralops.traceingestion.domain.repository.TraceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EmbeddedKafka(
        partitions = 3,
        topics = {"neuralops.traces.raw", "neuralops.traces.errors"},
        brokerProperties = {"log.retention.hours=1"}
)
class TraceIngestionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("neuralops_test")
            .withUsername("neuralops")
            .withPassword("neuralops_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers",
                () -> "${spring.embedded.kafka.brokers}");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private TraceRepository traceRepository;

    @AfterEach
    void cleanUp() {
        traceRepository.deleteAll();
        sessionRepository.deleteAll();
        agentRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/traces with valid request returns 202 and ACCEPTED status")
    void postTrace_validRequest_returns202() {
        TraceEventRequest request = buildValidRequest("integration-agent-1", "sess-int-001");

        ResponseEntity<TraceEventResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/traces",
                request,
                TraceEventResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("ACCEPTED");
        assertThat(response.getBody().traceId()).isNotBlank();
        assertThat(response.getBody().agentId()).isEqualTo("integration-agent-1");
    }

    @Test
    @DisplayName("POST /api/v1/traces persists the agent to the database")
    void postTrace_validRequest_persistsAgent() {
        TraceEventRequest request = buildValidRequest("new-integration-agent", "sess-int-002");

        restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/traces",
                request,
                TraceEventResponse.class
        );

        assertThat(agentRepository.findByAgentId("new-integration-agent")).isPresent();
    }

    @Test
    @DisplayName("POST /api/v1/traces with missing agentId returns 400 with RFC 7807 error")
    void postTrace_missingAgentId_returns400WithProblemDetail() {
        TraceEventRequest request = new TraceEventRequest(
                null, "sess-int-003", TraceType.LLM_CALL,
                null, 100L, null, null, null, null
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/traces",
                request,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("title");
        assertThat(response.getBody()).containsKey("fieldErrors");
    }

    @Test
    @DisplayName("Two traces for the same session update the session record rather than creating a duplicate")
    void postTwoTraces_sameSession_updatesSessionRecord() {
        TraceEventRequest first = buildValidRequest("session-test-agent", "shared-session");
        TraceEventRequest second = buildValidRequest("session-test-agent", "shared-session");

        restTemplate.postForEntity("http://localhost:" + port + "/api/v1/traces", first, TraceEventResponse.class);
        restTemplate.postForEntity("http://localhost:" + port + "/api/v1/traces", second, TraceEventResponse.class);

        assertThat(sessionRepository.findAll())
                .filteredOn(s -> "shared-session".equals(s.getSessionId()))
                .hasSize(1);
        assertThat(traceRepository.count()).isEqualTo(2);
    }

    private TraceEventRequest buildValidRequest(String agentId, String sessionId) {
        return new TraceEventRequest(
                agentId, sessionId, TraceType.LLM_CALL,
                Map.of("model", "llama3.1:8b"),
                750L, 500,
                new BigDecimal("0.0010"),
                Instant.now(),
                Map.of("env", "integration-test")
        );
    }
}
