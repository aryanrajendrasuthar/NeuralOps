Runbook: High Latency Alert — GatewayHighP99Latency
=====================================================

Alert name:  GatewayHighP99Latency
Severity:    Warning
Threshold:   p99 latency > 1000ms for 3 consecutive minutes
Owner:       Platform engineering on-call


What This Alert Means
---------------------

The gateway p99 latency has exceeded 1 second. This means 1 in 100 requests
is taking more than 1 second to complete. The SLA threshold is 500ms at p99.
This alert fires before the SLA is breached so engineers have time to act.

Common causes in order of probability:
  1. Downstream service (trace-ingestion or metrics-service) is slow or overloaded
  2. PostgreSQL or TimescaleDB is under write pressure
  3. Kafka producer is blocking due to broker backpressure
  4. Redis is slow or unreachable (circuit breaker may be open)
  5. JVM GC pause on one of the downstream services


Immediate Triage (< 5 minutes)
-------------------------------

1. Open the NeuralOps Platform Overview Grafana dashboard.

2. Check "Gateway p99 Latency" panel. Is this a spike or sustained elevation?
   A spike (< 30 seconds) is usually a GC pause or a single slow batch. A
   sustained elevation indicates an underlying problem.

3. Check "HTTP Error Rate by Service" panel. If a service shows elevated 5xx
   errors alongside high latency, that service is the root cause.

4. Check "JVM Heap Used" panel. If heap is above 85% on any service, GC pressure
   is likely the cause. This is a short-term fix situation — see "GC Pressure" below.

5. Check "Kafka Consumer Lag" panel. If lag is growing on metrics-service or
   alert-service, those services are falling behind, which causes backpressure
   upstream.


Diagnosis Steps
---------------

Step 1: Identify which downstream service is slow.

  kubectl -n neuralops get pods
  # Check for pods in CrashLoopBackOff, OOMKilled, or high restart counts

  kubectl -n neuralops top pods
  # Check CPU and memory usage per pod

Step 2: Check the slow service logs.

  kubectl -n neuralops logs -l app=trace-ingestion-service --tail=100
  # Look for: "HikariPool timeout", "Failed to record trace", PostgreSQL errors

  kubectl -n neuralops logs -l app=metrics-service --tail=100
  # Look for: Redis errors, circuit breaker open messages

Step 3: Check PostgreSQL.

  kubectl -n neuralops exec -it deployment/postgres -- psql -U neuralops -c \
    "SELECT count(*), wait_event_type, wait_event FROM pg_stat_activity GROUP BY 2, 3;"
  # Look for: many sessions in "Lock" or "IO" wait states

Step 4: Check Redis.

  kubectl -n neuralops exec -it deployment/redis -- redis-cli INFO stats
  # Look for: rejected_connections, blocked_clients > 0


Remediation
-----------

HikariCP pool exhaustion (log message: "Connection is not available, request timed out"):
  - Temporarily increase DB_POOL_MAX by editing the ConfigMap and rolling the deployment
  - Long term: add a PostgreSQL replica or scale the service deployment

GC pressure (heap > 85%, frequent GC pauses in logs):
  - Immediately: kubectl -n neuralops rollout restart deployment/trace-ingestion-service
    This clears heap and restarts the pod. Kubernetes will keep other replicas running.
  - Long term: Increase memory limits in values.yaml and redeploy

Redis circuit breaker open (log message: "CircuitBreaker 'redis' is OPEN"):
  - Check Redis health: kubectl -n neuralops exec -it deployment/redis -- redis-cli PING
  - If Redis is down: real-time metrics are degraded but trace ingestion continues
    (the circuit breaker prevents Redis failures from affecting the ingestion path)
  - Restart Redis if it is unhealthy. Circuit breaker will close automatically after
    the configured wait duration (15 seconds) once Redis responds normally.

Kafka backpressure (producer log: "RecordTooLargeException" or slow send):
  - Check Kafka broker health via the Kafka UI or kafka-topics.sh
  - Check disk space on Kafka brokers: the most common cause of broker slowness
    under retention pressure is disk > 85% full
  - Reduce retention if disk is the problem: kafka-configs.sh --alter
    --entity-type topics --entity-name neuralops.traces.raw
    --add-config retention.bytes=10737418240


Escalation
----------

If latency does not return below 500ms p99 within 20 minutes of starting triage,
escalate to the platform engineering team lead and open a P1 incident.

Document all actions taken and their timestamps in the incident channel. When
the incident is resolved, file a post-mortem using the template at
docs/runbooks/post-mortem-template.md.
