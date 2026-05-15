Sprint 3 — Metrics Engine and Real-Time Analytics
===================================================

Duration: Day 3, approximately 3 hours
Goal: Build the metrics computation engine that transforms raw trace events into meaningful real-time analytics
including latency histograms, cost tracking, error rates, and throughput.

What You Will Learn
-------------------

By the end of this sprint you will understand what percentiles are and why p50/p95/p99 latency matters more
than average latency for understanding user experience. You will understand how Redis sorted sets work and when
to use Redis versus a relational database for metric storage. You will understand what linear regression is and
how to apply it to cost forecasting. You will understand what TimescaleDB continuous aggregates are and how
they differ from standard materialized views. You will understand how to design analytics API endpoints that
return useful data efficiently. You will also understand the performance engineering concepts that make systems
scale: latency versus throughput, HikariCP connection pooling, circuit breakers, horizontal scaling, Flyway
migrations, exactly-once Kafka semantics, and how to read a k6 load test result.

Deliverables
------------

metrics-service Kafka consumer processing raw traces and computing metric aggregates.

Real-time metric computations: p50, p95, p99 latency per agent; error rate per agent; token usage per session;
cost per agent per hour and per day; throughput in traces per second.

Redis data structures for real-time metric storage using sorted sets for leaderboards and hashes for per-agent
statistics.

REST APIs: GET /api/v1/metrics/agents/{agentId}/latency, GET /api/v1/metrics/agents/{agentId}/cost,
GET /api/v1/metrics/overview.

cost-analytics-service with cost breakdown computation, budget alert thresholds, and linear regression
cost forecasting.

TimescaleDB continuous aggregates for efficient time-window rollup queries.

LEARNING.md updated with Sprint 3 teaching section including the full performance engineering curriculum.

Commit Checkpoints
------------------

CHECKPOINT 3A: After metrics-service Kafka consumer and Redis storage.
Suggested commit message: "feat(metrics): implement real-time metrics computation with redis storage"

CHECKPOINT 3B: After metrics query REST APIs.
Suggested commit message: "feat(metrics): add metrics query apis with latency and cost endpoints"

CHECKPOINT 3C: After cost-analytics-service with linear regression forecasting.
Suggested commit message: "feat(cost-analytics): implement cost breakdown service with linear regression forecasting"

Acceptance Criteria
-------------------

The metrics-service consumer reads from neuralops.traces.raw and updates Redis within 100ms.

GET /api/v1/metrics/agents/{agentId}/latency returns p50, p95, p99 values from Redis.

The cost-analytics-service produces a forecast for the next 7 days from linear regression on the last 30 days.

TimescaleDB continuous aggregates return hourly rollup results in under 100ms.

All REST endpoints return 200 within 200ms at p99 under 50 concurrent requests.
