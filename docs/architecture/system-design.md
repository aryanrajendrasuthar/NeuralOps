NeuralOps System Design
=======================

Version: 1.0
Status: Active
Last Updated: 2025-05-14

Overview
--------

NeuralOps is an AI Agent Observability and Reliability Platform built to give engineering teams the same level of
operational visibility into their AI workloads that Datadog gives them into traditional infrastructure. When a company
deploys LLM-powered agents — whether for customer support, code review, data analysis, or autonomous task
execution — those agents fail silently, consume unpredictable amounts of compute budget, and behave
inconsistently in ways that are invisible without purpose-built instrumentation.

NeuralOps solves this by intercepting every meaningful event in an agent's lifecycle: each LLM call, each tool
invocation, each decision branching point, and each error. It streams those events in real time through a
high-throughput ingestion pipeline, computes aggregate metrics and anomaly scores, fires alerts when behavior
deviates from baselines, and surfaces all of this through an operational dashboard built for the engineers who run
production AI systems.

This document describes the architecture of NeuralOps version 1.0 — its components, data stores, communication
patterns, and the reasoning behind each significant design choice.

Architecture Overview
---------------------

NeuralOps is structured as a set of independently deployable microservices, each with a narrow, well-defined
responsibility. Services communicate asynchronously through Apache Kafka for all data-plane events and
synchronously via HTTP for control-plane queries. All services are stateless; shared state lives in Redis, PostgreSQL,
or TimescaleDB.

ASCII Architecture Diagram
---------------------------

```
                         External AI Agents / SDKs
                                    |
                              (HTTPS / SDK)
                                    |
                         +----------v----------+
                         |    API Gateway       |
                         |  (Spring Cloud       |
                         |   Gateway, port 8080)|
                         +----------+----------+
                                    |
              +---------------------+---------------------+
              |                     |                     |
    +---------v--------+  +---------v--------+  +--------v---------+
    | trace-ingestion  |  |   user-service   |  |  metrics-service  |
    |    service       |  |  (auth, API keys)|  | (query endpoints) |
    |  port 8081       |  |  port 8085       |  |  port 8082        |
    +---------+--------+  +------------------+  +--------+----------+
              |                                          |
              | publish                                  | subscribe
              |                                          |
    +---------v----------+                               |
    |   Apache Kafka     |<------------------------------+
    | neuralops.traces.raw                               |
    | neuralops.traces.errors                            |
    | neuralops.anomalies                                |
    +----+---------------+
         |
         +----------------------------+----------------------------+
         |                            |                            |
+--------v--------+        +----------v-------+        +----------v-------+
| metrics-service |        | alert-service    |        | ai-analysis-     |
| (Kafka consumer)|        | (Kafka consumer) |        | service (Python) |
| port 8082       |        | port 8083        |        | port 8090        |
+--------+--------+        +----------+-------+        +----------+-------+
         |                            |                            |
         |                 +----------v-------+                    |
         |                 | Webhook / Notif  |                    |
         |                 | Delivery         |                    |
         |                 +------------------+                    |
         |                                                         |
+--------v--------+  +-------------------+  +--------------------+
|  TimescaleDB    |  |   PostgreSQL       |  |  Redis             |
| (time-series    |  | (traces, agents,  |  | (real-time metrics,|
|  metrics)       |  |  sessions, alerts)|  |  session state,    |
| port 5433       |  | port 5432         |  |  rate limiting)    |
+-----------------+  +-------------------+  | port 6379          |
                                            +--------------------+

                     +------------------+
                     |   Frontend        |
                     | (React 18 + TS   |
                     |  + Tailwind CSS) |
                     | port 3000        |
                     +------------------+

                     +------------------+  +------------------+
                     |   Prometheus     |  |    Grafana        |
                     | (metrics scrape) |  | (dashboards)      |
                     | port 9090        |  | port 3001         |
                     +------------------+  +------------------+

                     +------------------+
                     |   Ollama         |
                     | (local LLM,      |
                     |  llama3.1:8b,    |
                     |  nomic-embed)    |
                     | port 11434       |
                     +------------------+
```

Service Responsibilities
------------------------

API Gateway (services/gateway)

The gateway is the single entry point for all external traffic. It handles request routing to downstream services,
JWT validation, rate limiting via Redis token buckets, and request/response logging for audit purposes. Implemented
using Spring Cloud Gateway. All routes are defined declaratively in application.yml. The gateway does not contain
business logic — it is a pure infrastructure component.

Trace Ingestion Service (services/trace-ingestion-service)

This service owns the primary data inflow. It exposes a REST API that AI agents or SDK wrappers call to report
trace events. On receiving a request, the service validates the payload against the TraceEvent schema, enriches it
with a server-side timestamp and ingestion ID, and publishes it to the Kafka topic `neuralops.traces.raw`. The
service is designed for high write throughput and minimal latency — it does no synchronous persistence to the
database on the hot path. Database writes are handled downstream by the metrics service after Kafka consumption.

Metrics Service (services/metrics-service)

The metrics service serves two roles. As a Kafka consumer, it reads from `neuralops.traces.raw`, computes
aggregate statistics, writes time-series records to TimescaleDB, and updates real-time counters in Redis. As an
HTTP server, it exposes the query API that the frontend and external consumers use to retrieve latency percentiles,
cost summaries, error rates, and throughput data. Separating reads and writes through Kafka means ingestion load
cannot starve query traffic.

Alert Service (services/alert-service)

The alert service owns the rules engine and notification delivery. It consumes from both `neuralops.traces.raw` and
`neuralops.anomalies`. Against raw traces, it evaluates threshold rules (latency, error rate, cost). Against anomaly
events from the AI analysis service, it evaluates model-scored deviation thresholds. When a rule fires, the service
writes an alert record to PostgreSQL, deduplicates against recent alert history, and dispatches notifications via
webhook or in-platform SSE.

Cost Analytics Service (services/cost-analytics-service)

This service maintains a dedicated view of spending across agents, sessions, and time windows. It subscribes to the
processed trace stream and accumulates token counts, estimated cost in USD, and request counts by agent and
session. It exposes cost breakdown APIs and implements a simple linear regression forecast to project spend over
configurable time horizons. Budget alert thresholds are configurable per agent.

User Service (services/user-service)

The user service handles authentication, authorization, user registration, team management, and API key lifecycle.
It issues JWT access tokens and long-lived refresh tokens. API keys — used by agents and SDKs to authenticate
trace submissions — are hashed with bcrypt before storage; the plaintext is returned exactly once at creation time.
Team-based access control determines which agents and dashboards a user can see.

AI Analysis Service (services/ai-analysis-service)

This is the Python FastAPI service responsible for machine learning workloads that are impractical in Java. It runs
an Isolation Forest model trained on recent trace data for each agent to produce anomaly scores. Anomaly events
are published to the `neuralops.anomalies` Kafka topic. The service also exposes a REST API for querying anomaly
history. LLM-based analysis uses Ollama running llama3.1:8b locally — no external API calls, zero cost.

Data Stores
-----------

PostgreSQL is the system of record for all durable, relational data: user accounts, teams, API keys, agent
registrations, session records, raw trace event metadata, alert rules, alert history, and notification preferences.
Schema migrations are managed by Flyway, ensuring zero-downtime changes.

TimescaleDB is a PostgreSQL extension that adds hypertable support — time-partitioned table storage optimized
for time-series data. It stores computed metric snapshots: latency percentile values, cost aggregates, and error
rates at configurable time intervals. Continuous aggregates allow efficient rollup queries (hourly, daily) without
re-scanning the full dataset.

Redis stores all ephemeral, high-frequency state: real-time metric accumulators, agent health status, rate limiting
counters, and SSE connection state. Redis sorted sets serve as latency leaderboards (agents ranked by p95 latency).
Redis hashes store per-agent stat snapshots that the dashboard polls at one-second intervals. Nothing in Redis is
the source of truth — all data is derived from the Kafka stream and can be rebuilt on cache miss or restart.

Apache Kafka is the backbone of the data plane. All trace events flow through Kafka before reaching any
persistent store. This decouples ingestion throughput from downstream processing speed, allows multiple
consumers to independently process the same stream, and provides replay capability for rebuilding derived state
after failures. Topics are configured with three partitions minimum to allow horizontal consumer scaling.

Communication Patterns
----------------------

The trace ingestion path is entirely asynchronous: the agent SDK calls the REST API, the trace-ingestion-service
validates and publishes to Kafka, and returns 202 Accepted. The agent does not wait for persistence. This allows
the ingestion service to sustain high throughput without back-pressure from downstream I/O.

The query path is synchronous: the frontend calls the metrics service or cost analytics service via the gateway,
which reads from Redis for real-time data or TimescaleDB for historical aggregates. Target p99 for all query
endpoints is 200ms.

Cross-service communication for anomaly detection follows the same async pattern — the AI analysis service
consumes from Kafka and publishes anomaly events back to Kafka, which the alert service then consumes.

Technology Decisions
--------------------

All architectural decisions, including the choice of Kafka over alternatives, the microservices decomposition, and
the Java/Spring Boot stack selection, are documented as Architecture Decision Records in docs/decisions/. Each
ADR records the context, the options considered, the decision made, and the consequences — following the
lightweight ADR format popularized by Michael Nygard.

System Requirements
-------------------

To run the full NeuralOps stack locally:

RAM: 16GB minimum, 32GB recommended. Ollama with llama3.1:8b plus all Docker containers consume 10-14GB
combined under load.

CPU: 4 cores minimum, 8 cores recommended. Kafka, TimescaleDB, and Ollama are all CPU-intensive under load.

Storage: 20GB free disk space for Docker images, PostgreSQL data, and Ollama model weights.

OS: macOS, Linux, or Windows with WSL2. Native Windows is not supported.

If RAM is below 16GB, set Docker Desktop memory limit to 12GB and do not run more than three services
concurrently outside of Docker.
