package com.neuralops.alertservice.service;

import com.neuralops.alertservice.domain.entity.AlertEventEntity;
import com.neuralops.alertservice.domain.entity.AlertEventEntity.WebhookStatus;
import com.neuralops.alertservice.domain.repository.AlertEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class WebhookDeliveryService {

    private final AlertEventRepository alertEventRepository;
    private final RestClient restClient;
    private final int maxRetries;
    private final Counter deliveredCounter;
    private final Counter failedCounter;

    public WebhookDeliveryService(
            AlertEventRepository alertEventRepository,
            RestClient.Builder restClientBuilder,
            @Value("${neuralops.alerting.webhook-timeout-seconds:10}") int timeoutSeconds,
            @Value("${neuralops.alerting.webhook-max-retries:3}") int maxRetries,
            MeterRegistry meterRegistry) {
        this.alertEventRepository = alertEventRepository;
        this.restClient = restClientBuilder
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("User-Agent", "NeuralOps-AlertService/1.0")
                .build();
        this.maxRetries = maxRetries;
        this.deliveredCounter = Counter.builder("neuralops.webhook.delivered")
                .description("Webhook deliveries that succeeded")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("neuralops.webhook.failed")
                .description("Webhook deliveries that exhausted retries")
                .register(meterRegistry);
    }

    @Async
    public void deliverAsync(AlertEventEntity event) {
        attemptDelivery(event);
    }

    @Scheduled(fixedDelayString = "${neuralops.alerting.retry-interval-ms:60000}")
    @Transactional
    public void retryPendingWebhooks() {
        List<AlertEventEntity> pending = alertEventRepository.findByWebhookStatusInOrderByTriggeredAtAsc(
                List.of(WebhookStatus.PENDING, WebhookStatus.FAILED));

        for (AlertEventEntity event : pending) {
            if (event.getWebhookAttempts() < maxRetries) {
                attemptDelivery(event);
            } else {
                event.setWebhookStatus(WebhookStatus.FAILED);
                alertEventRepository.save(event);
                failedCounter.increment();
                log.warn("Alert event id={} exhausted {} retries, marking FAILED", event.getId(), maxRetries);
            }
        }
    }

    private void attemptDelivery(AlertEventEntity event) {
        String webhookUrl = event.getRule().getWebhookUrl();
        Map<String, Object> payload = buildPayload(event);

        try {
            restClient.post()
                    .uri(webhookUrl)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            event.setWebhookStatus(WebhookStatus.DELIVERED);
            event.setWebhookAttempts(event.getWebhookAttempts() + 1);
            event.setLastAttemptAt(Instant.now());
            alertEventRepository.save(event);

            deliveredCounter.increment();
            log.info("Webhook delivered: eventId={} url={}", event.getId(), webhookUrl);
        } catch (Exception ex) {
            event.setWebhookAttempts(event.getWebhookAttempts() + 1);
            event.setLastAttemptAt(Instant.now());
            event.setWebhookStatus(WebhookStatus.PENDING);
            alertEventRepository.save(event);
            log.warn("Webhook delivery failed: eventId={} attempt={} url={}: {}",
                    event.getId(), event.getWebhookAttempts(), webhookUrl, ex.getMessage());
        }
    }

    private Map<String, Object> buildPayload(AlertEventEntity event) {
        return Map.of(
                "eventId", event.getId(),
                "agentId", event.getAgentId(),
                "ruleId", event.getRule().getId(),
                "metric", event.getRule().getMetric().name(),
                "operator", event.getRule().getOperator().name(),
                "threshold", event.getRule().getThreshold(),
                "metricValue", event.getMetricValue(),
                "triggeredAt", event.getTriggeredAt().toString()
        );
    }
}
