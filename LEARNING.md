NeuralOps — Engineering Learning Journal
=========================================

This file is updated at the end of every sprint with in-depth explanations of the engineering concepts
encountered that sprint. The intended reader is a developer who understands basic programming but has not
yet worked in an industry engineering role. These are the things you would learn working alongside a senior
engineer — explained directly rather than buried in documentation or assumed as prior knowledge.

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
