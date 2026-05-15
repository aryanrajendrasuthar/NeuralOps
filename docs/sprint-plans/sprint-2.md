Sprint 2 — Core Trace Ingestion Pipeline
==========================================

Duration: Day 2, approximately 3 hours
Goal: Build the heart of NeuralOps — the pipeline that receives trace events from AI agents, validates them,
streams them through Kafka, and persists them durably.

What You Will Learn
-------------------

By the end of this sprint you will understand what event-driven architecture means and how it differs from
request-response architecture. You will understand how Kafka topics and partitions work and why partitioning
matters for throughput. You will understand what a hypertable is in TimescaleDB and why time-series data
needs different storage than relational data. You will understand the RFC 7807 Problem Details standard and
why standardized error formats make APIs easier to consume. You will understand what Testcontainers is and
why integration testing against real infrastructure catches bugs that unit tests miss. You will understand how
database indexing works and why it is critical at scale.

Deliverables
------------

trace-ingestion-service: Full REST API endpoint POST /api/v1/traces with validation.

TraceEvent schema with all required fields: agentId, sessionId, traceType, payload, latencyMs, tokenCount,
estimatedCostUsd, timestamp, metadata.

Kafka producer publishing to neuralops.traces.raw and neuralops.traces.errors.

Kafka consumer in metrics-service subscribing to neuralops.traces.raw.

PostgreSQL schema: traces, agents, sessions tables with appropriate indexes.

TimescaleDB hypertable for time-series metric records.

Full OpenAPI documentation for all trace ingestion endpoints.

Input validation with RFC 7807 error responses.

Unit tests with minimum 80% coverage for trace ingestion logic.

Integration tests using Testcontainers for Kafka and PostgreSQL.

LEARNING.md updated with Sprint 2 teaching section.

Commit Checkpoints
------------------

CHECKPOINT 2A: After trace-ingestion-service REST API and Kafka producer.
Suggested commit message: "feat(trace-ingestion): implement trace event ingestion api with kafka producer"

CHECKPOINT 2B: After PostgreSQL schema and TimescaleDB hypertable setup.
Suggested commit message: "feat(persistence): add postgresql schema and timescaledb hypertable for trace storage"

CHECKPOINT 2C: After unit tests and integration tests.
Suggested commit message: "test(trace-ingestion): add unit and integration tests for trace ingestion pipeline"

Acceptance Criteria
-------------------

POST /api/v1/traces with a valid payload returns 202 Accepted and the event appears in neuralops.traces.raw.

POST /api/v1/traces with a missing required field returns 400 with RFC 7807 format.

The metrics-service Kafka consumer logs received events from the topic.

The PostgreSQL traces table has a composite index on (agent_id, created_at).

The TimescaleDB hypertable is partitioned by time correctly.

Unit test coverage report shows 80% or above for the ingestion service.

Integration tests pass using real Kafka and PostgreSQL containers.
