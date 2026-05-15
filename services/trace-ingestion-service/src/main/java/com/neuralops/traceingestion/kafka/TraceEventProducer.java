package com.neuralops.traceingestion.kafka;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TraceEventProducer {

    private final KafkaTemplate<String, TraceEventMessage> kafkaTemplate;

    @Value("${neuralops.kafka.topics.traces-raw}")
    private String tracesRawTopic;

    @Value("${neuralops.kafka.topics.traces-errors}")
    private String tracesErrorsTopic;

    @CircuitBreaker(name = "kafka-producer", fallbackMethod = "handleKafkaFailure")
    public CompletableFuture<SendResult<String, TraceEventMessage>> publishTraceEvent(TraceEventMessage message) {
        log.debug("Publishing trace event to {}: traceId={} agentId={} traceType={}",
                tracesRawTopic, message.traceId(), message.agentId(), message.traceType());

        return kafkaTemplate.send(tracesRawTopic, message.agentId(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish trace event traceId={} agentId={}: {}",
                                message.traceId(), message.agentId(), ex.getMessage());
                    } else {
                        log.debug("Published trace event traceId={} to partition={} offset={}",
                                message.traceId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    public void publishErrorEvent(TraceEventMessage message, String errorReason) {
        log.warn("Publishing trace error event traceId={} reason={}", message.traceId(), errorReason);
        kafkaTemplate.send(tracesErrorsTopic, message.agentId(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish error event traceId={}: {}", message.traceId(), ex.getMessage());
                    }
                });
    }

    @SuppressWarnings("unused")
    private CompletableFuture<SendResult<String, TraceEventMessage>> handleKafkaFailure(
            TraceEventMessage message, Throwable t) {
        log.error("Kafka circuit breaker open for trace event traceId={}: {}", message.traceId(), t.getMessage());
        return CompletableFuture.failedFuture(t);
    }
}
