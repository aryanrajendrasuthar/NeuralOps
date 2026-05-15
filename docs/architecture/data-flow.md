NeuralOps Data Flow
===================

Version: 1.0
Status: Active
Last Updated: 2025-05-14

This document traces the path of a single trace event from the moment an AI agent emits it to the moment it
appears as a metric on the NeuralOps dashboard. Understanding this flow in detail is essential for debugging
production issues, reasoning about latency, and knowing which component to look at when data is missing or
delayed.

Trace Event Lifecycle
---------------------

Step 1 — Agent Emits a Trace

An AI agent integrated with NeuralOps (via SDK or direct HTTP) calls the trace ingestion API when a significant
event occurs. These events include LLM calls, tool invocations, decision branch points, and errors. A typical LLM
call trace payload looks like this:

```json
{
  "agentId":      "customer-support-agent-v2",
  "sessionId":    "sess_01HXK3M9QT",
  "traceType":    "LLM_CALL",
  "payload": {
    "model":      "llama3.1:8b",
    "promptTokens": 1247,
    "completionTokens": 312
  },
  "latencyMs":          1843,
  "tokenCount":          1559,
  "estimatedCostUsd":    0.0023,
  "timestamp":          "2025-05-14T18:32:01.234Z",
  "metadata": {
    "environment": "production",
    "version":     "2.4.1"
  }
}
```

The agent sends this to POST /api/v1/traces on the API gateway with its API key in the Authorization header.

Step 2 — API Gateway Authentication

The Spring Cloud Gateway receives the request. It validates the JWT or API key against the user service, checks
rate limiting counters in Redis (token bucket per API key), and routes the request to the trace-ingestion-service.
If the API key is invalid or the rate limit is exceeded, the gateway returns 401 or 429 respectively without
forwarding the request downstream. This validation adds less than 5ms to the request path under normal load.

Step 3 — Trace Ingestion Service Validation

The trace-ingestion-service receives the routed request. It performs schema validation: required fields must be
present, traceType must be one of the defined enum values, latencyMs must be non-negative, estimatedCostUsd
must be a non-negative decimal. If validation fails, the service returns a 400 response following the RFC 7807
Problem Details format:

```json
{
  "type":     "https://neuralops.io/errors/validation-failure",
  "title":    "Trace event validation failed",
  "status":   400,
  "detail":   "Field 'agentId' is required and must not be blank",
  "instance": "/api/v1/traces"
}
```

If validation passes, the service enriches the event with a server-side ingestion timestamp and a generated
traceId (UUID v4), then publishes the enriched event to Kafka.

Step 4 — Kafka Producer Publish

The trace-ingestion-service's Kafka producer publishes to the topic `neuralops.traces.raw`. The producer is
configured with `acks=all` to ensure the message is written to all in-sync replicas before acknowledgment —
this provides durability at the cost of a small latency increase, which is acceptable given the target of under 50ms
for producer acknowledgment. The service returns 202 Accepted to the agent immediately after the Kafka
acknowledgment, without waiting for any downstream processing.

If the Kafka publish fails (broker unavailable, timeout), the trace-ingestion-service returns 503 to the agent, which
is expected to retry. The service uses Resilience4j circuit breakers to trip open after five consecutive failures,
preventing thread exhaustion while the broker is down.

Step 5 — Metrics Service Kafka Consumer

The metrics-service has a Kafka consumer group subscribed to `neuralops.traces.raw`. Each consumer reads
events from its assigned partition. On receiving an event, the metrics service:

1. Updates the per-agent latency histogram in Redis by incrementing the appropriate bucket.
2. Increments the per-agent token counter and cost accumulator in Redis hashes.
3. Increments the per-agent trace-type counter for error rate calculation.
4. Writes a time-series record to the TimescaleDB `agent_metrics` hypertable for historical queries.
5. Updates the agent's last-seen timestamp in Redis.

All Redis operations use pipelining where possible to reduce round-trip count. The TimescaleDB write uses a
prepared statement with batching — metrics are flushed to the database every 500ms rather than per-event, to
reduce I/O overhead at high throughput.

Step 6 — AI Analysis Service Kafka Consumer

Simultaneously, the ai-analysis-service Python process has its own consumer group on `neuralops.traces.raw`.
It maintains a rolling window of latency and error rate observations per agent. On each event:

1. The new latency value is appended to the agent's rolling buffer (last 1000 observations).
2. The Isolation Forest model trained on recent baseline data scores the new point.
3. If the anomaly score exceeds the configured threshold, the service publishes an anomaly event to
   `neuralops.anomalies` with the agent ID, score, timestamp, and contributing features.
4. The model is retrained on a background thread every 15 minutes using the accumulated buffer.

The Isolation Forest runs entirely in-process using scikit-learn — no external ML platform required.

Step 7 — Alert Service Evaluation

The alert-service consumes from both `neuralops.traces.raw` and `neuralops.anomalies`. For raw traces, it
evaluates threshold rules: if latencyMs exceeds 2000ms, if error rate (rolling 5-minute window) exceeds 5%, or
if hourly cost exceeds the configured budget for the agent. For anomaly events, it evaluates the anomaly score
against a configurable sensitivity threshold.

When an alert fires:
1. The alert service queries its deduplication cache (Redis) to check if an identical alert has fired within the
   configured cooldown window (default 5 minutes).
2. If not deduplicated, it writes an alert record to PostgreSQL (alert_events table).
3. It dispatches the notification — either as a webhook POST to the configured endpoint, or as an SSE event
   pushed to connected dashboard clients.

Step 8 — Dashboard Display

The frontend React application polls the metrics-service for updated data on a one-second interval using
Server-Sent Events. When the SSE stream delivers an alert event, the dashboard updates the anomaly feed in
real time. Metric cards (p95 latency, total cost, active agents) are updated from the Redis-backed summary
endpoint, which returns pre-computed values with sub-5ms read latency.

A user navigating to an agent's detail view triggers a historical query: the frontend calls
GET /api/v1/metrics/agents/{agentId}/latency?window=24h, which the metrics service resolves against
TimescaleDB continuous aggregates — pre-computed hourly rollups that allow this query to return in under 100ms
regardless of how many raw events are in the hypertable.

Error Paths
-----------

If Kafka is unavailable: The trace-ingestion-service circuit breaker trips after five failures. While open, all
incoming trace requests receive 503. The frontend shows a degraded-mode banner. Kafka consumer lag is
monitored via Prometheus; when the broker recovers and consumers resume, the lag metric drives a Grafana
alert to confirm recovery.

If Redis is unavailable: The metrics service falls back to PostgreSQL for real-time metric queries. Dashboard
latency increases but data remains correct. Alert service deduplication degrades gracefully — if the Redis check
fails, the alert fires rather than silently suppressing, which is the correct failure mode (false positive is safer than
false negative for alerting).

If TimescaleDB is unavailable: Historical queries fail with 503. Real-time data from Redis remains available.
The Resilience4j circuit breaker prevents connection pool exhaustion.

If the AI analysis service is unavailable: Anomaly events stop flowing. Threshold-based alerts continue to work
unaffected. The alert service logs the gap in anomaly coverage and exposes a metric that Grafana surfaces as
a warning.

Data Retention
--------------

Raw trace events in PostgreSQL are retained for 90 days. TimescaleDB hypertable chunks older than 90 days are
dropped automatically via the TimescaleDB chunk lifecycle policy. Redis data is ephemeral and may be lost on
restart — all Redis state is derived from Kafka and can be rebuilt. Kafka topics are configured with a 72-hour
retention window, long enough to replay after most outage scenarios.
