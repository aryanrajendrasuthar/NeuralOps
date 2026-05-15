package com.neuralops.metrics.kafka;

import com.neuralops.metrics.domain.entity.AgentMetricEntity;
import com.neuralops.metrics.domain.repository.AgentMetricRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class TraceEventConsumer {

    private final AgentMetricRepository agentMetricRepository;
    private final Counter tracesProcessedCounter;
    private final Counter tracesFailedCounter;

    private final List<AgentMetricEntity> pendingFlush = new CopyOnWriteArrayList<>();

    public TraceEventConsumer(AgentMetricRepository agentMetricRepository, MeterRegistry meterRegistry) {
        this.agentMetricRepository = agentMetricRepository;
        this.tracesProcessedCounter = Counter.builder("neuralops.metrics.traces.processed")
                .description("Total trace events processed by the metrics consumer")
                .register(meterRegistry);
        this.tracesFailedCounter = Counter.builder("neuralops.metrics.traces.failed")
                .description("Total trace events that failed processing in the metrics consumer")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${neuralops.kafka.topics.traces-raw}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "traceEventKafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(ConsumerRecord<String, Map<String, Object>> record, Acknowledgment acknowledgment) {
        try {
            Map<String, Object> payload = record.value();
            AgentMetricEntity metric = mapToMetricEntity(payload);
            agentMetricRepository.save(metric);

            tracesProcessedCounter.increment();
            log.debug("Processed trace event from partition={} offset={} agentId={}",
                    record.partition(), record.offset(), metric.getAgentId());

            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process trace event from partition={} offset={}: {}",
                    record.partition(), record.offset(), ex.getMessage(), ex);
            tracesFailedCounter.increment();
            acknowledgment.acknowledge();
        }
    }

    private AgentMetricEntity mapToMetricEntity(Map<String, Object> event) {
        String agentId = (String) event.get("agentId");
        String traceType = event.get("traceType") != null ? event.get("traceType").toString() : "UNKNOWN";
        Number latencyMs = (Number) event.get("latencyMs");
        Number tokenCount = (Number) event.get("tokenCount");
        Number costUsd = (Number) event.get("estimatedCostUsd");

        String timestampStr = (String) event.get("eventTimestamp");
        Instant time = timestampStr != null ? Instant.parse(timestampStr) : Instant.now();

        return AgentMetricEntity.builder()
                .time(time)
                .agentId(agentId)
                .traceType(traceType)
                .latencyMs(latencyMs != null ? latencyMs.longValue() : 0L)
                .tokenCount(tokenCount != null ? tokenCount.intValue() : null)
                .costUsd(costUsd != null ? BigDecimal.valueOf(costUsd.doubleValue()) : null)
                .isError("ERROR".equals(traceType))
                .build();
    }
}
