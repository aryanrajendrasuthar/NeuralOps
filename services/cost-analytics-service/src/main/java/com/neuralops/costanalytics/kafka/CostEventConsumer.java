package com.neuralops.costanalytics.kafka;

import com.neuralops.costanalytics.domain.repository.AgentDailyCostRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Slf4j
@Component
public class CostEventConsumer {

    private final AgentDailyCostRepository agentDailyCostRepository;
    private final BigDecimal defaultCostPerThousandTokens;
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;

    public CostEventConsumer(
            AgentDailyCostRepository agentDailyCostRepository,
            @Value("${neuralops.cost.usd-per-1k-tokens-default:0.002}") BigDecimal defaultCostPerThousandTokens,
            MeterRegistry meterRegistry) {
        this.agentDailyCostRepository = agentDailyCostRepository;
        this.defaultCostPerThousandTokens = defaultCostPerThousandTokens;
        this.eventsProcessedCounter = Counter.builder("neuralops.cost.events.processed")
                .description("Total trace events processed by the cost analytics consumer")
                .register(meterRegistry);
        this.eventsFailedCounter = Counter.builder("neuralops.cost.events.failed")
                .description("Total trace events that failed cost processing")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${neuralops.kafka.topics.traces-raw}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "costEventKafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(ConsumerRecord<String, Map<String, Object>> record, Acknowledgment acknowledgment) {
        try {
            Map<String, Object> event = record.value();
            String agentId = extractString(event, "agentId", "unknown");
            LocalDate costDate = extractEventDate(event);

            Number rawCost = (Number) event.get("estimatedCostUsd");
            Number rawTokens = (Number) event.get("tokenCount");
            long tokenCount = rawTokens != null ? rawTokens.longValue() : 0L;

            BigDecimal costUsd;
            if (rawCost != null) {
                costUsd = BigDecimal.valueOf(rawCost.doubleValue());
            } else if (tokenCount > 0) {
                costUsd = defaultCostPerThousandTokens
                        .multiply(BigDecimal.valueOf(tokenCount))
                        .divide(BigDecimal.valueOf(1000));
            } else {
                costUsd = BigDecimal.ZERO;
            }

            agentDailyCostRepository.upsertDailyCost(agentId, costDate, costUsd, 1L, tokenCount);

            eventsProcessedCounter.increment();
            log.debug("Cost recorded for agentId={} date={} costUsd={}", agentId, costDate, costUsd);

            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process cost event partition={} offset={}: {}",
                    record.partition(), record.offset(), ex.getMessage(), ex);
            eventsFailedCounter.increment();
            acknowledgment.acknowledge();
        }
    }

    private LocalDate extractEventDate(Map<String, Object> event) {
        Object ts = event.get("eventTimestamp");
        if (ts != null) {
            try {
                return java.time.Instant.parse(ts.toString())
                        .atOffset(ZoneOffset.UTC).toLocalDate();
            } catch (DateTimeParseException ex) {
                log.warn("Could not parse eventTimestamp '{}', defaulting to today", ts);
            }
        }
        return LocalDate.now(ZoneOffset.UTC);
    }

    private String extractString(Map<String, Object> event, String key, String defaultValue) {
        Object value = event.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
