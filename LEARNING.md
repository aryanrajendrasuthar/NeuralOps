NeuralOps — Engineering Learning Journal
=========================================

This file is updated at the end of every sprint with in-depth explanations of the engineering concepts
encountered that sprint. The intended reader is a developer who understands basic programming but has not
yet worked in an industry engineering role. These are the things you would learn working alongside a senior
engineer — explained directly rather than buried in documentation or assumed as prior knowledge.

Sprint 2 — Core Trace Ingestion Pipeline
-----------------------------------------

What is Event-Driven Architecture?

In a request-response architecture, the caller waits. You call a function, it does its work, and it returns
a result. If that work takes 2 seconds, the caller blocks for 2 seconds. This is simple to reason about but
creates a hard coupling between the speed of the caller and the speed of the callee.

In an event-driven architecture, the caller publishes a fact — "this thing happened" — and moves on. The
downstream processing happens independently, at its own pace, by any number of interested parties. The
trace ingestion service does exactly this: it validates your event, publishes it to Kafka, and returns 202
Accepted. The metrics computation, alert evaluation, and anomaly detection all happen after the caller has
already received its response.

This design lets the ingestion service achieve sub-50ms response times even when downstream processing
takes seconds. It lets the metrics service fall behind briefly during a traffic spike and catch up later, without
that lag ever affecting the agent's experience. It lets you add a new consumer (say, an audit logging service)
without touching the ingestion service at all.

The trade-off is that you can no longer guarantee a synchronous result. You cannot say "I submitted this trace
and immediately check the metrics — they will be updated." There is an inherent propagation delay. For
observability systems, this is acceptable. For a payment system, it might not be.

How Kafka Topics and Partitions Work

A Kafka topic is a named, ordered log. When a producer sends a message to a topic, it is appended to the
end of the log. Consumers read from the log by tracking their current offset — the position of the last message
they processed.

A topic is divided into partitions. Each partition is an independent ordered log on a single broker. Producers
choose which partition to write to (we use the agentId as the partition key, so all events from one agent land
in the same partition and are processed in order). A consumer group distributes partitions among its members
— with three partitions, three consumer instances each process exactly one partition in parallel.

This is why we configure Kafka topics with three partitions from the start. You cannot add partitions to an
existing topic without breaking the ordering guarantees for keys that were previously assigned to a different
partition. Sizing partitions correctly at the start is a real operational decision that experienced engineers make.

The offset is just an integer. Consumer groups commit their offset back to Kafka periodically. If a consumer
crashes and restarts, it reads from the last committed offset and reprocesses. This is why idempotent
processing matters — if the metrics service processes an event twice, the metric count should not be doubled.
Our implementation uses MANUAL_IMMEDIATE acknowledgment, which commits only after the event is
successfully processed.

What is RFC 7807 — Problem Details for HTTP APIs?

When an API returns an error, the client needs to understand what went wrong. The naive approach is to return
a string message: "Error: agentId is required." This works for humans reading logs but is hard to parse
programmatically. Different services use different formats, different field names, different status codes for
the same category of error. Client code ends up with fragile string-matching logic to handle errors.

RFC 7807 defines a standard JSON format for HTTP error responses:

```json
{
  "type":     "https://neuralops.io/errors/validation-failure",
  "title":    "Trace event validation failed",
  "status":   400,
  "detail":   "One or more fields failed validation. See 'fieldErrors' for details.",
  "instance": "/api/v1/traces",
  "fieldErrors": {
    "agentId": "agentId is required and must not be blank"
  }
}
```

The `type` URI uniquely identifies the error class and can point to documentation. The `title` is a short
human-readable summary that does not change between occurrences. The `detail` is the specific explanation
for this particular occurrence. The `status` is the HTTP status code, present in the body so clients can
access it without parsing headers.

Spring Boot 6 / Spring Framework 6 includes built-in ProblemDetail support — `ProblemDetail.forStatus()`
creates the correct structure, and Spring MVC serializes it with the correct `Content-Type:
application/problem+json` header automatically.

What is Testcontainers?

Testcontainers is a Java library that starts real Docker containers as part of your test setup and tears them
down when the tests finish. Instead of mocking your PostgreSQL repository or embedding an in-memory H2
database (which behaves differently from real PostgreSQL), you run the exact same PostgreSQL image in a
Docker container during the test.

This matters because mock databases lie. H2 does not enforce the same constraint checks as PostgreSQL. It
does not support JSONB columns, array types, or tsvector full-text search. It handles concurrency differently.
Tests that pass against H2 regularly fail against real PostgreSQL in production.

With Testcontainers, the integration test in TraceIngestionIntegrationTest starts a postgres:16-alpine
container at the beginning of the test class, runs all tests against it, and removes the container afterward.
The test is slower than a unit test (3-10 seconds for container startup) but catches a whole class of bugs
that unit tests cannot.

The @DynamicPropertySource annotation lets the test override Spring's datasource URL with the actual JDBC
URL of the container, which Testcontainers generates dynamically (it assigns a random host port to avoid
conflicts with other tests running simultaneously).

How Database Indexing Works and Why It Matters at Scale

An index is a separate data structure that the database maintains alongside your table. It stores the indexed
column values in sorted order, along with a pointer to the row. When a query filters on an indexed column,
the database does a B-tree lookup (log n time) instead of scanning every row in the table (n time).

At 1,000 traces per second, the traces table grows by 86 million rows per day. Without an index on
(agent_id, ingested_at), a query like "show me the last 100 traces for agent X" would require scanning all
86 million rows to find the matching ones. With the composite index, it finds the first matching row in
microseconds and reads the next 99 contiguous rows from the sorted index.

The composite index (agent_id, ingested_at DESC) is specifically designed for the Trace Explorer query
pattern: filter by agent_id, order by ingested_at descending. The column order in a composite index
matters — (agent_id, ingested_at) supports queries that filter by agent_id alone or by agent_id and time.
It does not support queries that filter by ingested_at alone without agent_id.

The EXPLAIN ANALYZE command in PostgreSQL shows you the query plan and actual execution time for any
query. Any query that shows "Seq Scan" (sequential scan) on a large table is a performance problem that
must be fixed before it reaches production. This is something you will be asked about in any senior
engineering interview.

Sprint 1 — Foundation and Architecture
---------------------------------------

What Does a Senior Engineer Actually Do on Day 1?

When a senior engineer starts a project, they do not write a single line of production code on the first day.
They write documents. This surprises most people who are new to the industry, because writing feels less
productive than coding. But the reality is that the cost of a wrong architectural decision compounds over time
in ways that a wrong line of code does not. Changing one function costs you an hour. Changing the message
broker from RabbitMQ to Kafka after six months of development costs you weeks.

On Day 1, a senior engineer asks: What problem are we actually solving? Who uses this system and how? What
are the non-negotiable performance requirements? What data needs to be stored and how will it change over
time? What happens when any given component fails? What are the regulatory or compliance constraints? Only
after those questions have written answers does it make sense to choose a database or write a controller.

The documents in docs/architecture/ and docs/decisions/ are the answers to those questions for NeuralOps.
They exist not because documentation is bureaucracy, but because they are the cheapest mechanism for
finding flaws in a design before the design is frozen in code.

What is a Microservices Architecture?

A monolith is a single deployable program. When you build a web application in a single Java or Python process
— one binary, one database connection, one set of configuration — that is a monolith. Monoliths are not bad.
Most successful software started as a monolith. They are simple to develop, test, and deploy when the team is
small and the problem is understood.

A microservices architecture splits the application into multiple independently deployable processes, each
responsible for one narrow domain. Each process has its own database connection pool, its own configuration,
its own deployment lifecycle. They communicate over the network — through HTTP APIs or message queues.

The reason companies move to microservices is not because microservices are inherently better. They solve
specific problems that appear at scale: the ability to deploy one component without redeploying everything
else; the ability to scale just the part of the system that is under load without scaling the parts that are not;
the ability to use a different programming language for a component that genuinely needs it (our Python AI
analysis service is an example of this); and fault isolation — a bug in the cost analytics service should not
take down the trace ingestion service.

The cost of microservices is real: network latency between services, distributed tracing complexity, more
infrastructure to operate, and harder integration testing. Do not adopt microservices to seem sophisticated.
Adopt them when the benefits are clearly needed. For NeuralOps, the AI analysis service requiring Python and
the ingestion service requiring independent horizontal scaling are concrete justifications.

What is Apache Kafka?

Apache Kafka is a distributed log. That description is precise but requires unpacking.

A log is an append-only sequence of records. You can read any record at any position in the log by specifying
its offset. Unlike a queue, reading a record does not remove it. Multiple readers can read the same log
independently, each tracking their own position.

Kafka distributes this log across a cluster of brokers (server processes). It partitions each topic into ordered
segments, with each partition assigned to a broker. Producers write to a partition by key (for NeuralOps, by
agentId). Consumers in a consumer group each read from a subset of partitions, allowing parallel consumption.

Why does this matter? In a traditional message queue (RabbitMQ, SQS), a message is consumed and deleted.
If the consumer crashes after receiving but before processing, the message may be lost. Kafka retains messages
for a configurable window (72 hours in NeuralOps). If the metrics service crashes, it restarts from its last
committed offset and reprocesses the missed events — no data loss, no external recovery mechanism needed.

This is the foundation of event sourcing: your database is derived from the log. If you lose your derived state,
you replay the log to rebuild it. This is why Kafka is the right choice for a system where multiple consumers
(metrics, alerting, AI analysis) need to independently process the same stream of trace events.

What is Docker Compose?

Docker Compose is a tool that lets you define and run multi-container Docker applications with a single YAML
file. Without it, you would need to run separate `docker run` commands for Kafka, ZooKeeper, PostgreSQL,
TimescaleDB, Redis, Prometheus, and Grafana — each with the right environment variables, volume mounts,
and network settings — every time you wanted to start your development environment.

With Docker Compose, all of that is captured in docker-compose.yml. `docker compose up -d` starts every
service in dependency order in the background. `docker compose down` stops and removes all containers.
`docker compose logs -f metrics-service` follows the logs of a specific service.

The key concept to understand is that Docker containers are isolated processes running in their own filesystem
and network namespace. They communicate through a virtual Docker network. From inside a container,
`postgres:5432` resolves to the PostgreSQL container because they share the same Compose network. From
your laptop, you reach it at `localhost:5432` because the port is published to the host.

In production, Docker Compose is not used — Kubernetes takes its place. But Docker Compose is the right
tool for local development because it requires no Kubernetes cluster, no cloud account, and no infrastructure
beyond Docker Desktop.

What is an Architecture Decision Record?

An Architecture Decision Record (ADR) is a short document that captures a significant technical decision:
the context that led to it, the options that were considered, the decision that was made, and the consequences
of that decision — both positive and negative.

ADRs exist because the reasoning behind decisions has a shorter half-life than the decisions themselves.
Six months from now, when someone asks "why are we using Kafka instead of RabbitMQ?", the code cannot
answer that question. An ADR can. This is especially important when the original decision-maker has left
the team — which happens in every company, every year.

At companies like Spotify, Zalando, and ThoughtWorks, writing ADRs before implementing significant
architectural changes is a standard engineering practice. On some teams, a change that affects the
architecture cannot be merged without an associated ADR. This may seem like overhead, but it forces
engineers to articulate their reasoning before they write code — which frequently reveals flaws in the
reasoning that code would have hidden for months.

The format used in NeuralOps follows Michael Nygard's lightweight ADR format: Date, Status, Context,
Decision, Rationale, Consequences, Rejected Alternatives. It is intentionally minimal — these documents
should be readable in under five minutes.

What is a Hypertable in TimescaleDB?

TimescaleDB is a PostgreSQL extension that adds efficient storage and querying for time-series data. The
central concept is the hypertable: a table that is automatically partitioned by time behind the scenes.

When you insert a row with a timestamp, TimescaleDB routes that row to the appropriate chunk — an internal
subtable covering a specific time range (e.g., one day). Old chunks are compressed or dropped automatically.
Queries that filter by time range scan only the relevant chunks, not the entire table. This is why TimescaleDB
queries on time-series data are dramatically faster than equivalent queries on a regular PostgreSQL table with
the same number of rows.

For NeuralOps, the agent_metrics hypertable stores latency percentile values, error counts, and cost
aggregates indexed by timestamp. A query for "give me the p95 latency for agent X over the last 24 hours"
scans exactly 24 chunks rather than an ever-growing monolithic table.

Regular PostgreSQL remains the right choice for relational data (users, agents, alert rules) where queries
filter by ID or relationship rather than time range. Using the right database for the right data type is the
correct approach — not forcing all data into one storage system.
