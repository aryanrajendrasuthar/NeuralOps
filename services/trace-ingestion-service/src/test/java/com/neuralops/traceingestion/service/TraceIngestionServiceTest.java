package com.neuralops.traceingestion.service;

import com.neuralops.traceingestion.api.dto.TraceEventRequest;
import com.neuralops.traceingestion.api.dto.TraceEventResponse;
import com.neuralops.traceingestion.api.dto.TraceType;
import com.neuralops.traceingestion.domain.entity.AgentEntity;
import com.neuralops.traceingestion.domain.entity.SessionEntity;
import com.neuralops.traceingestion.domain.repository.AgentRepository;
import com.neuralops.traceingestion.domain.repository.SessionRepository;
import com.neuralops.traceingestion.domain.repository.TraceRepository;
import com.neuralops.traceingestion.kafka.TraceEventMessage;
import com.neuralops.traceingestion.kafka.TraceEventProducer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceIngestionServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private TraceRepository traceRepository;

    @Mock
    private TraceEventProducer traceEventProducer;

    private TraceIngestionService service;

    @BeforeEach
    void setUp() {
        service = new TraceIngestionService(
                agentRepository,
                sessionRepository,
                traceRepository,
                traceEventProducer,
                new SimpleMeterRegistry()
        );
        when(traceEventProducer.publishTraceEvent(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    @DisplayName("ingest returns ACCEPTED response with generated traceId for a valid request")
    void ingest_validRequest_returnsAcceptedWithTraceId() {
        TraceEventRequest request = buildValidRequest();
        when(agentRepository.findByAgentId(anyString())).thenReturn(Optional.empty());
        when(sessionRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(traceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TraceEventResponse response = service.ingest(request);

        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(response.traceId()).isNotBlank();
        assertThat(response.agentId()).isEqualTo("test-agent-1");
        assertThat(response.sessionId()).isEqualTo("sess-abc-123");
        assertThat(response.traceType()).isEqualTo(TraceType.LLM_CALL);
        assertThat(response.ingestedAt()).isNotNull();
    }

    @Test
    @DisplayName("ingest publishes a Kafka message with matching agentId and traceType")
    void ingest_validRequest_publishesKafkaMessage() {
        TraceEventRequest request = buildValidRequest();
        when(agentRepository.findByAgentId(anyString())).thenReturn(Optional.empty());
        when(sessionRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(traceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ingest(request);

        ArgumentCaptor<TraceEventMessage> messageCaptor = ArgumentCaptor.forClass(TraceEventMessage.class);
        verify(traceEventProducer, times(1)).publishTraceEvent(messageCaptor.capture());

        TraceEventMessage published = messageCaptor.getValue();
        assertThat(published.agentId()).isEqualTo("test-agent-1");
        assertThat(published.sessionId()).isEqualTo("sess-abc-123");
        assertThat(published.traceType()).isEqualTo(TraceType.LLM_CALL);
        assertThat(published.latencyMs()).isEqualTo(850L);
        assertThat(published.traceId()).isNotBlank();
    }

    @Test
    @DisplayName("ingest uses server timestamp when request timestamp is null")
    void ingest_nullTimestamp_usesServerTime() {
        TraceEventRequest request = new TraceEventRequest(
                "test-agent-1", "sess-abc-123", TraceType.TOOL_INVOCATION,
                Map.of("tool", "search"), 120L, null, null, null, null
        );
        when(agentRepository.findByAgentId(anyString())).thenReturn(Optional.empty());
        when(sessionRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(traceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        TraceEventResponse response = service.ingest(request);
        Instant after = Instant.now();

        assertThat(response.ingestedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("ingest updates existing agent last-seen timestamp instead of creating a duplicate")
    void ingest_existingAgent_updatesLastSeenAt() {
        TraceEventRequest request = buildValidRequest();
        AgentEntity existingAgent = AgentEntity.builder()
                .agentId("test-agent-1")
                .lastSeenAt(Instant.now().minusSeconds(3600))
                .createdAt(Instant.now().minusSeconds(86400))
                .updatedAt(Instant.now().minusSeconds(3600))
                .build();

        when(agentRepository.findByAgentId("test-agent-1")).thenReturn(Optional.of(existingAgent));
        when(sessionRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(traceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ingest(request);

        ArgumentCaptor<AgentEntity> agentCaptor = ArgumentCaptor.forClass(AgentEntity.class);
        verify(agentRepository).save(agentCaptor.capture());
        assertThat(agentCaptor.getValue().getLastSeenAt()).isAfter(Instant.now().minusSeconds(5));
    }

    @Test
    @DisplayName("ingest updates existing session last-event-at instead of creating a duplicate")
    void ingest_existingSession_updatesLastEventAt() {
        TraceEventRequest request = buildValidRequest();
        SessionEntity existingSession = SessionEntity.builder()
                .sessionId("sess-abc-123")
                .agentId("test-agent-1")
                .startedAt(Instant.now().minusSeconds(600))
                .lastEventAt(Instant.now().minusSeconds(60))
                .build();

        when(agentRepository.findByAgentId(anyString())).thenReturn(Optional.empty());
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.findBySessionId("sess-abc-123")).thenReturn(Optional.of(existingSession));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(traceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ingest(request);

        ArgumentCaptor<SessionEntity> sessionCaptor = ArgumentCaptor.forClass(SessionEntity.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getLastEventAt()).isAfter(Instant.now().minusSeconds(5));
    }

    private TraceEventRequest buildValidRequest() {
        return new TraceEventRequest(
                "test-agent-1",
                "sess-abc-123",
                TraceType.LLM_CALL,
                Map.of("model", "llama3.1:8b", "promptTokens", 500),
                850L,
                750,
                new BigDecimal("0.0015"),
                Instant.now(),
                Map.of("environment", "test")
        );
    }
}
