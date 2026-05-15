package com.neuralops.alertservice.kafka;

import com.neuralops.alertservice.service.AlertRuleEngine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class AnomalyAlertConsumer {

    private final AlertRuleEngine alertRuleEngine;
    private final Counter processedCounter;
    private final Counter failedCounter;

    public AnomalyAlertConsumer(AlertRuleEngine alertRuleEngine, MeterRegistry meterRegistry) {
        this.alertRuleEngine = alertRuleEngine;
        this.processedCounter = Counter.builder("neuralops.alert.anomaly.processed")
                .description("Anomaly events evaluated by the alert rule engine")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("neuralops.alert.anomaly.failed")
                .description("Anomaly events that failed alert evaluation")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${neuralops.kafka.topics.anomalies}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "alertEventKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Map<String, Object>> record, Acknowledgment acknowledgment) {
        try {
            alertRuleEngine.evaluateAnomalyEvent(record.value());
            processedCounter.increment();
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to evaluate anomaly event partition={} offset={}: {}",
                    record.partition(), record.offset(), ex.getMessage(), ex);
            failedCounter.increment();
            acknowledgment.acknowledge();
        }
    }
}
