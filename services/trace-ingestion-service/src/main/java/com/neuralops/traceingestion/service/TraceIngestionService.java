package com.neuralops.traceingestion.service;

import com.neuralops.traceingestion.api.dto.TraceEventRequest;
import com.neuralops.traceingestion.api.dto.TraceEventResponse;
import com.neuralops.traceingestion.domain.entity.AgentEntity;
import com.neuralops.traceingestion.domain.entity.SessionEntity;
import com.neuralops.traceingestion.domain.entity.TraceEntity;
import com.neuralops.traceingestion.domain.repository.AgentRepository;
import com.neuralops.traceingestion.domain.repository.SessionRepository;
import com.neuralops.traceingestion.domain.repository.TraceRepository;
import com.neuralops.traceingestion.kafka.TraceEventMessage;
import com.neuralops.traceingestion.kafka.TraceEventProducer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class TraceIngestionService {

    private final AgentRepository agentRepository;
    private final SessionRepository sessionRepository;
    private final TraceRepository traceRepository;
    private final TraceEventProducer traceEventProducer;
    private final Counter tracesAcceptedCounter;
    private final Counter tracesRejectedCounter;
    private final Timer ingestionTimer;

    public TraceIngestionService(
            AgentRepository agentRepository,
            SessionRepository sessionRepository,
            TraceRepository traceRepository,
            TraceEventProducer traceEventProducer,
            MeterRegistry meterRegistry) {
        this.agentRepository = agentRepository;
        this.sessionRepository = sessionRepository;
        this.traceRepository = traceRepository;
        this.traceEventProducer = traceEventProducer;

        this.tracesAcceptedCounter = Counter.builder("neuralops.traces.accepted")
                .description("Total trace events accepted for processing")
                .register(meterRegistry);
        this.tracesRejectedCounter = Counter.builder("neuralops.traces.rejected")
                .description("Total trace events rejected due to errors")
                .register(meterRegistry);
        this.ingestionTimer = Timer.builder("neuralops.ingestion.duration")
                .description("Time taken to process and publish a trace event")
                .register(meterRegistry);
    }

    @Transactional
    public TraceEventResponse ingest(TraceEventRequest request) {
        return ingestionTimer.record(() -> doIngest(request));
    }

    private TraceEventResponse doIngest(TraceEventRequest request) {
        String traceId = UUID.randomUUID().toString();
        Instant ingestedAt = Instant.now();
        Instant eventTimestamp = request.timestamp() != null ? request.timestamp() : ingestedAt;

        log.info("Ingesting trace event traceId={} agentId={} sessionId={} traceType={}",
                traceId, request.agentId(), request.sessionId(), request.traceType());

        upsertAgent(request.agentId(), ingestedAt);
        upsertSession(request.sessionId(), request.agentId(), eventTimestamp);

        TraceEntity traceEntity = TraceEntity.builder()
                .traceId(traceId)
                .agentId(request.agentId())
                .sessionId(request.sessionId())
                .traceType(request.traceType().name())
                .latencyMs(request.latencyMs())
                .tokenCount(request.tokenCount())
                .estimatedCostUsd(request.estimatedCostUsd())
                .eventTimestamp(eventTimestamp)
                .ingestedAt(ingestedAt)
                .build();

        traceRepository.save(traceEntity);

        TraceEventMessage message = new TraceEventMessage(
                traceId,
                request.agentId(),
                request.sessionId(),
                request.traceType(),
                request.payload(),
                request.latencyMs(),
                request.tokenCount(),
                request.estimatedCostUsd(),
                eventTimestamp,
                ingestedAt,
                request.metadata()
        );

        traceEventProducer.publishTraceEvent(message)
                .exceptionally(ex -> {
                    log.error("Kafka publish failed for traceId={}, routing to error topic: {}",
                            traceId, ex.getMessage());
                    traceEventProducer.publishErrorEvent(message, ex.getMessage());
                    tracesRejectedCounter.increment();
                    return null;
                });

        tracesAcceptedCounter.increment();

        return new TraceEventResponse(
                traceId,
                request.agentId(),
                request.sessionId(),
                request.traceType(),
                ingestedAt,
                "ACCEPTED"
        );
    }

    private void upsertAgent(String agentId, Instant now) {
        agentRepository.findByAgentId(agentId).ifPresentOrElse(
                agent -> {
                    agent.setLastSeenAt(now);
                    agentRepository.save(agent);
                },
                () -> {
                    AgentEntity agent = AgentEntity.builder()
                            .agentId(agentId)
                            .createdAt(now)
                            .updatedAt(now)
                            .lastSeenAt(now)
                            .build();
                    agentRepository.save(agent);
                    log.info("Registered new agent: {}", agentId);
                }
        );
    }

    private void upsertSession(String sessionId, String agentId, Instant eventTimestamp) {
        sessionRepository.findBySessionId(sessionId).ifPresentOrElse(
                session -> {
                    session.setLastEventAt(eventTimestamp);
                    sessionRepository.save(session);
                },
                () -> {
                    SessionEntity session = SessionEntity.builder()
                            .sessionId(sessionId)
                            .agentId(agentId)
                            .startedAt(eventTimestamp)
                            .lastEventAt(eventTimestamp)
                            .build();
                    sessionRepository.save(session);
                }
        );
    }
}
