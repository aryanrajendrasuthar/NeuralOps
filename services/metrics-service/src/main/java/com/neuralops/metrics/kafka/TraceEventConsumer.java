package com.neuralops.metrics.kafka;

import com.neuralops.metrics.domain.entity.AgentMetricEntity;
import com.neuralops.metrics.domain.repository.AgentMetricRepository;
import com.neuralops.metrics.redis.RedisMetricsStore;
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
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class TraceEventConsumer {

    private final AgentMetricRepository agentMetricRepository;
    private final RedisMetricsStore redisMetricsStore;
    private final Counter tracesProcessedCounter;
    private final Counter tracesFailedCounter;

    public TraceEventConsumer(
            AgentMetricRepository agentMetricRepository,
            RedisMetricsStore redisMetricsStore,
            MeterRegistry meterRegistry) {
        this.agentMetricRepository = agentMetricRepository;
        this.redisMetricsStore = redisMetricsStore;
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
            Map<String, Object> event = record.value();
            AgentMetricEntity metric = mapToMetricEntity(event);

            agentMetricRepository.save(metric);

            String traceId = extractString(event, "traceId", UUID.randomUUID().toString());
            Number tokenCount = (Number) event.get("tokenCount");
            Number costUsd = (Number) event.get("estimatedCostUsd");
            BigDecimal cost = costUsd != null ? BigDecimal.valueOf(costUsd.doubleValue()) : null;

            redisMetricsStore.recordTraceEvent(
                    metric.getAgentId(),
                    traceId,
                    metric.getLatencyMs(),
                    tokenCount != null ? tokenCount.intValue() : null,
                    cost,
                    metric.getIsError()
            );
            redisMetricsStore.markAgentActive(metric.getAgentId());

            tracesProcessedCounter.increment();
            log.debug("Processed trace event partition={} offset={} agentId={}",
                    record.partition(), record.offset(), metric.getAgentId());

            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process trace event partition={} offset={}: {}",
                    record.partition(), record.offset(), ex.getMessage(), ex);
            tracesFailedCounter.increment();
            acknowledgment.acknowledge();
        }
    }

    private AgentMetricEntity mapToMetricEntity(Map<String, Object> event) {
        String agentId = extractString(event, "agentId", "unknown");
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

    private String extractString(Map<String, Object> event, String key, String defaultValue) {
        Object value = event.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
