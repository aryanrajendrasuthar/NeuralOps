ADR-001: Apache Kafka as the Event Streaming Backbone
======================================================

Date: 2025-05-14
Status: Accepted
Deciders: Platform Engineering Team

Context
-------

NeuralOps must ingest trace events from AI agents at high volume — the target throughput is 1,000 events per
second per Kafka partition, with the system expected to scale to tens of thousands of events per second as
customer adoption grows. These events drive multiple independent downstream consumers: the metrics engine,
the alert service, and the AI anomaly detection service. Each consumer has different processing semantics and
different performance characteristics.

The ingestion pipeline must not lose events on downstream failure, must support replaying the event stream for
rebuilding derived state, and must decouple producer throughput from consumer processing speed so that a slow
anomaly detection job does not slow down metric computation.

We evaluated four options: Apache Kafka, RabbitMQ, AWS Kinesis, and a direct database queue pattern using
PostgreSQL LISTEN/NOTIFY.

Decision
--------

We chose Apache Kafka.

Rationale
---------

Kafka's log-based architecture is architecturally better suited to this use case than any of the alternatives for
three reasons.

First, Kafka retains the full event stream on disk for a configurable retention window (we use 72 hours). This
means any consumer can replay the stream independently. If the metrics service crashes and loses in-memory
state, it can restart from its last committed offset and reprocess without data loss. RabbitMQ messages are
consumed and deleted — there is no replay. This makes Kafka the only viable choice for a system where derived
state (Redis metrics, TimescaleDB aggregates) must be rebuildable from the raw event log.

Second, Kafka's consumer group model allows multiple independent consumer groups to read the same topic
partition independently, each at its own pace. The metrics service, alert service, and AI analysis service each
have their own consumer group. Adding a new consumer (for example, a future audit logging service) requires
zero changes to existing code — it simply subscribes to the existing topic. In RabbitMQ, each message is routed
to a queue and consumed exactly once per queue; achieving fan-out requires exchange bindings and one queue
per consumer, which adds operational complexity and does not scale the same way.

Third, Kafka partitions allow horizontal consumer scaling that maps directly to our throughput requirements.
With three partitions on `neuralops.traces.raw`, we can run three consumer instances in the metrics service
consumer group, each processing a third of the traffic in parallel. Scaling to six instances means rebalancing
to six partitions — a configuration change, not a code change. RabbitMQ supports competing consumers on a
queue, but this requires consumers to be identical — it does not support the independent consumer group model.

Consequences
------------

Operational complexity: Kafka requires ZooKeeper (in versions below 3.x) or KRaft mode (3.x and above). We
use the Confluent distribution with ZooKeeper for the 3.x version in our Docker Compose setup because it is
more widely documented and has mature tooling. This adds one more infrastructure component to manage
compared to RabbitMQ.

Ordering guarantees: Kafka guarantees order only within a partition, not across partitions. For NeuralOps, this
is acceptable because we key trace events by agentId — all traces for a given agent land in the same partition
and are processed in order.

Latency: Kafka's batch-oriented producer has higher baseline latency than RabbitMQ for individual message
delivery. We configure `linger.ms=5` and `batch.size=65536` to allow batching at high throughput while keeping
latency under 50ms for producer acknowledgment at moderate load. For the trace ingestion use case, 202
Accepted is returned after Kafka acknowledgment, not after downstream processing — so Kafka latency does
not affect the agent's perceived response time beyond the initial ACK.

Rejected Alternatives
---------------------

RabbitMQ: Lacks log-based retention and independent consumer group replay. Suitable for task queues and
RPC-style workloads but not for event-sourced pipelines where stream replay is required.

AWS Kinesis: Requires an AWS account and costs money at any non-trivial throughput. Violates the zero-budget
constraint of this project. Architecturally similar to Kafka but with lower partition limits and higher per-shard cost.

PostgreSQL LISTEN/NOTIFY: Simple to operate but fundamentally not a message broker. No persistence of
published events, no consumer groups, no throughput above a few hundred events per second, and no replay
capability. Suitable for lightweight notification patterns within a single service, not for a distributed event
pipeline.
