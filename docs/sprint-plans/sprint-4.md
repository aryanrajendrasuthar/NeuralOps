Sprint 4 — Alerting Engine and AI-Powered Anomaly Detection
=============================================================

Duration: Day 4, approximately 3 hours
Goal: Build the intelligence layer — threshold-based alerting and AI-powered anomaly detection that identifies
when agents are misbehaving before humans notice.

What You Will Learn
-------------------

By the end of this sprint you will understand what the Isolation Forest algorithm is and how it detects anomalies
in unlabeled time-series data. You will understand what alert fatigue is and how deduplication solves it. You will
understand how Python and Java services communicate in a microservices architecture through shared Kafka
topics. You will understand what webhooks are and how platforms like PagerDuty use them. You will understand
the difference between rules-based and ML-based alerting and when each is appropriate.

Deliverables
------------

alert-service: Rule engine evaluating threshold-based alerts for latency, error rate, and cost.

Alert delivery channels: webhook POST delivery and in-platform Server-Sent Events notifications.

PostgreSQL schema for alert rules, alert history, and notification preferences.

ai-analysis-service: Isolation Forest anomaly detection consuming from neuralops.traces.raw.

Streaming anomaly scoring: each trace event is scored and anomaly events published to neuralops.anomalies.

REST API for anomaly history and current anomaly status per agent.

Alert deduplication: identical alerts suppressed within a configurable cooldown window (default 5 minutes).

LEARNING.md updated with Sprint 4 teaching section.

Commit Checkpoints
------------------

CHECKPOINT 4A: After alert-service rule engine and database schema.
Suggested commit message: "feat(alerting): implement rule-based alert engine with threshold detection"

CHECKPOINT 4B: After ai-analysis-service Isolation Forest and Kafka streaming.
Suggested commit message: "feat(ai-analysis): add isolation forest anomaly detection with kafka streaming"

CHECKPOINT 4C: After webhook delivery and alert deduplication.
Suggested commit message: "feat(alerting): add webhook notification delivery and alert deduplication"

Acceptance Criteria
-------------------

A trace event with latencyMs > 2000 triggers an alert within 2 seconds.

Sending the same alert-triggering event twice within 5 minutes produces exactly one alert record.

The ai-analysis-service scores at least 100 events per second without falling behind the Kafka partition.

Anomaly events appear in neuralops.anomalies within 500ms of the triggering trace event.

Webhook delivery retries with exponential backoff on failure.
