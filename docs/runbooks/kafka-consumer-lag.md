Runbook: Kafka Consumer Lag — KafkaConsumerLagHigh / KafkaConsumerLagCritical
===============================================================================

Alert name:  KafkaConsumerLagHigh (warning > 10,000), KafkaConsumerLagCritical (critical > 50,000)
Severity:    Warning / Critical
Owner:       Platform engineering on-call


What This Alert Means
---------------------

A Kafka consumer group is falling behind the producer. The lag value is the
number of unprocessed messages between the consumer's committed offset and the
current end of the partition log.

A lag of 10,000 at 1,000 events/second means the consumer is 10 seconds behind
real-time. A lag of 50,000 means 50 seconds. For an observability platform, this
means metrics and alerts are delayed by that amount.

Consumer groups and their typical lag causes:

  neuralops-metrics-service  — TimescaleDB write slow, or service restarting
  neuralops-alert-service    — Database write slow, or webhook delivery blocking
  neuralops-cost-analytics   — PostgreSQL upsert slow
  neuralops-ai-analysis      — Isolation Forest training blocking the event loop


Immediate Triage (< 5 minutes)
-------------------------------

1. Identify which consumer group has the lag:

  kubectl -n neuralops exec -it deployment/kafka -- \
    kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --all-groups \
    | grep -v 0$
  # The last column is LAG. Find the group with the highest value.

2. Check whether the lag is growing or stable:

  Watch the "Kafka Consumer Lag" Grafana panel for 2 minutes. A stable lag means
  the consumer is processing at the same rate as production (temporarily behind but
  not worsening). A growing lag means the consumer cannot keep up.

3. Check the pods for the lagging service:

  kubectl -n neuralops get pods -l app=<service-name>
  kubectl -n neuralops top pods -l app=<service-name>


Diagnosis
---------

Case 1: Pods are healthy but lag is growing.

  The service is processing events but cannot keep up with the ingest rate.
  This is a throughput problem, not a crash.

  Solutions:
    a. Temporarily increase replicas (each replica processes one partition):
       kubectl -n neuralops scale deployment/<service-name> --replicas=6
       Note: you cannot have more replicas processing a topic than there are
       partitions. NeuralOps topics have 3 partitions, so 3 is the effective
       maximum for parallelism unless partitions are increased.

    b. Check if the bottleneck is the database:
       kubectl -n neuralops logs -l app=<service-name> --tail=50
       Look for slow query warnings or HikariCP timeout messages.

Case 2: Pods are crashing or restarting.

  kubectl -n neuralops describe pod <pod-name>
  # Check "Last State" for the OOMKilled reason or exit code

  kubectl -n neuralops logs <pod-name> --previous
  # Check logs from the previous (crashed) container

  Common causes:
    - OOMKilled: increase memory limits in values.yaml
    - Exception in consumer: check logs for the specific error; fix and redeploy
    - Redis/PostgreSQL connection refused: dependency is down

Case 3: Lag appeared suddenly after a deployment.

  A deployment that introduces a bug in the consumer can cause it to crash on every
  event, leaving offset committed but no work done, or to process very slowly.

  Rollback immediately:
    kubectl -n neuralops rollout undo deployment/<service-name>

  Then review the failed deployment's changes for any consumer processing logic.


Recovering from a Large Backlog
--------------------------------

When lag exceeds 100,000, the backlog may take minutes to clear even after fixing
the root cause. During recovery:

1. Alert the team that metrics and alerts will be delayed until the backlog clears.
   Do not page during recovery unless fresh events are also failing.

2. Scale the consumer deployment to the maximum effective replicas (= partition count):
   kubectl -n neuralops scale deployment/<service-name> --replicas=3

3. Monitor the lag trend in Grafana. It should decrease at a rate roughly equal to
   the throughput capacity of the service.

4. Once lag returns to zero, scale replicas back to the normal value and document
   the incident.


Increasing Partition Count
--------------------------

Warning: increasing partition count on an existing topic breaks ordering guarantees
for any key that was previously routed to a different partition. This is a schema
change that requires careful coordination.

If throughput permanently exceeds what 3 replicas can handle:
  1. Create a new topic with more partitions (e.g., neuralops.traces.raw.v2)
  2. Dual-write from the ingestion service to both topics during migration
  3. Cut over consumers to the new topic one at a time
  4. Deprecate the old topic after all consumers have migrated

Do not run kafka-topics.sh --alter on the existing topic to change partition count
without first understanding the ordering implications for every consumer.
