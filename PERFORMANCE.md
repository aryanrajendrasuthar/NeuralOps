NeuralOps Performance Baseline and SLA Reference
==================================================

This document defines the performance targets, load test methodology, and tuning
guidance for the NeuralOps platform. It is the authoritative reference for on-call
engineers responding to latency or throughput alerts.


Service Level Objectives
------------------------

The following SLOs apply to production traffic. Breaching any SLO for more than
5 minutes triggers a P2 incident. Breaching for more than 15 minutes triggers a P1.

Service                   Endpoint                     p95 Target   p99 Target   Error Rate
------------------------  ---------------------------  -----------  -----------  ----------
Gateway (inbound)         POST /api/v1/traces           150ms        500ms        < 1%
Trace Ingestion           POST /api/v1/traces           100ms        400ms        < 0.5%
Metrics Service           GET  /api/v1/metrics/**       50ms         200ms        < 1%
Alert Service             GET  /api/v1/alerts/**        100ms        500ms        < 1%
Cost Analytics            GET  /api/v1/cost/**          200ms        800ms        < 1%
User Service              POST /api/v1/auth/**          200ms        600ms        < 0.5%
AI Analysis               GET  /api/v1/anomalies/**     500ms        3000ms       < 2%

Platform-level targets:
  - End-to-end trace ingestion to metrics availability: < 2 seconds (p95)
  - Alert fire-to-webhook-delivery: < 5 seconds (p95)
  - Dashboard data freshness: < 30 seconds for real-time panels
  - Availability: 99.9% monthly (43.8 minutes allowable downtime)


Load Test Methodology
---------------------

Tool: k6 (https://k6.io)
Test script: tests/load/k6-trace-ingestion.js

The ramp profile is:
  30s  → 50 virtual users   (warm-up)
  2m   → 200 virtual users  (steady state)
  3m   → 500 virtual users  (peak load)
  2m   → 200 virtual users  (cool-down)
  30s  → 0 virtual users    (drain)

Running the load test:

  # Against local Docker Compose environment
  k6 run tests/load/k6-trace-ingestion.js

  # Against a deployed environment
  BASE_URL=https://neuralops.example.com k6 run tests/load/k6-trace-ingestion.js

  # With result output to InfluxDB for Grafana
  k6 run --out influxdb=http://localhost:8086/k6 tests/load/k6-trace-ingestion.js

Pass/fail criteria (enforced by k6 thresholds):
  - http_req_duration p95 < 500ms
  - http_req_duration p99 < 2000ms
  - error rate < 1%


Baseline Results (3-replica Docker Compose on M2 MacBook Pro, 16GB RAM)
------------------------------------------------------------------------

These results establish the baseline on a single developer machine. Production
results on properly sized Kubernetes nodes will differ significantly.

Peak throughput:        ~400 req/s sustained
p50 ingestion latency:  45ms
p95 ingestion latency:  180ms
p99 ingestion latency:  620ms
Error rate:             0.2%
Redis write p99:        8ms
Kafka produce p99:      25ms
PostgreSQL write p99:   35ms


Performance Tuning Reference
-----------------------------

Trace Ingestion Service

The primary bottleneck under high load is the PostgreSQL write for the traces table.
If p99 latency exceeds 400ms, check:

1. HikariCP pool exhaustion: `neuralops_hikaricp_connections_pending` > 0 for more
   than 30 seconds means requests are waiting for a database connection. Increase
   `DB_POOL_MAX` or add replicas.

2. PostgreSQL connection count: Each service instance holds up to 20 connections.
   At 5 replicas, that is 100 connections. PostgreSQL defaults support 100
   max_connections. Increase `max_connections` in the PostgreSQL config or deploy
   PgBouncer as a connection pooler.

3. Kafka producer linger: `linger.ms=5` batches small bursts. If throughput is
   consistently above 500 req/s, increase `batch.size` from 65536 to 131072 to
   improve Kafka producer throughput.

Metrics Service

The metrics service has two write paths: TimescaleDB (durable) and Redis (real-time).
Redis writes are pipelined and rarely the bottleneck. If TimescaleDB writes slow down:

1. Check the continuous aggregate refresh interval. The default 5-minute window
   means the refresh job competes with writes. Increase to 15 minutes if writes
   are the priority.

2. Check TimescaleDB chunk size. The default 1-hour chunk interval is correct for
   1,000 traces/second. If throughput is significantly lower, increase to 1 day
   to reduce chunk overhead.

Redis

Redis becomes a bottleneck when the latency sorted set grows beyond 10,000 entries
per agent and ZREMRANGEBYRANK runs during every write. The trim operation is O(log n)
but adds latency to the pipeline. If Redis p99 exceeds 20ms:

1. Increase `LATENCY_WINDOW_MAX_SIZE` to reduce trim frequency (at the cost of
   more memory per agent).
2. Move trim to a background scheduled task running every 60 seconds rather than
   inline with every write.

Rate Limiting

The gateway applies token-bucket rate limiting to POST /api/v1/traces:
  - Replenish rate: 100 tokens/second per IP
  - Burst capacity: 200 tokens

When a client is rate-limited, the gateway returns HTTP 429 with a
`Retry-After` header. Clients should implement exponential backoff starting
at 100ms and capping at 5 seconds.

To adjust rate limits by environment:
  KAFKA_BOOTSTRAP_SERVERS: ... (replenishRate and burstCapacity are configured
  in the gateway application.yml RequestRateLimiter filter)


Capacity Planning
-----------------

1,000 traces/second sustained load requires approximately:

  Trace Ingestion:   5 replicas × (2 CPU / 1GB)
  Metrics Service:   4 replicas × (2 CPU / 1GB)
  Kafka:             3 brokers, 3 partitions per topic, 72h retention
  PostgreSQL:        2 vCPU / 8GB RAM / 500GB SSD (traces table grows ~50GB/day)
  TimescaleDB:       2 vCPU / 8GB RAM / 200GB SSD (compressed, ~5GB/day after compression)
  Redis:             1 vCPU / 2GB RAM (sorted sets for 10,000 samples × N agents)
