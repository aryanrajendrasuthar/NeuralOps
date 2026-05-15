ADR-002: Microservices Architecture over Monolith
==================================================

Date: 2025-05-14
Status: Accepted
Deciders: Platform Engineering Team

Context
-------

NeuralOps has six distinct functional domains: trace ingestion, metric computation, alerting, cost analytics,
user management, and AI-powered anomaly detection. These domains have meaningfully different operational
profiles. The trace ingestion service must handle burst write throughput and prioritizes availability over
consistency. The AI analysis service runs CPU-intensive Python workloads that benefit from independent scaling.
The user service handles infrequent reads and writes but requires high consistency for security-critical data.

The question at the start of the project was whether to build these as one deployable unit (monolith) or as
separate services.

Decision
--------

We chose a microservices architecture with one service per functional domain.

Rationale
---------

The primary driver is independent scalability. At projected production load, the trace ingestion service will
receive many more requests per second than the user service. If both are in the same process, you cannot scale
the ingestion capacity without also scaling user management capacity — wasting resources. With separate
services, each can be scaled to its own replica count via Kubernetes HorizontalPodAutoscaler.

The secondary driver is language heterogeneity. The AI analysis service uses scikit-learn's Isolation Forest
algorithm, which is a Python library with no viable Java equivalent at the same ecosystem maturity. Packaging
Python and Java in the same deployable unit is not practical. Microservices allow the AI analysis service to be
a Python FastAPI process while all other services are Java Spring Boot — each in the right language for its job.

The third driver is fault isolation. If the AI analysis service crashes due to an out-of-memory error during model
training, the trace ingestion service continues accepting events and the metrics service continues computing
aggregates. In a monolith, any fatal error in any component brings down the whole system.

Consequences
------------

Operational overhead: Six services means six deployment units, six sets of configuration, six health checks, and
six log streams. We mitigate this with Docker Compose for local development (one `docker compose up` starts
everything) and Kubernetes manifests for production (one Helm chart installs the full stack).

Network latency: What was a function call in a monolith becomes an HTTP call across a network. We design the
data plane (trace events) to flow through Kafka — asynchronous, no blocking network calls on the hot path.
Synchronous inter-service calls are limited to the control plane (authentication, configuration lookup).

Distributed tracing: Debugging a request that spans multiple services is harder than debugging one that stays in
a single process. We use standard request-ID propagation through HTTP headers so that all log entries for a
given trace are correlated.

Testing complexity: Integration tests must spin up multiple services. We use Testcontainers to run Kafka,
PostgreSQL, and Redis as Docker containers in the test environment, which replicates the real topology without
requiring a full cluster.

Rejected Alternatives
---------------------

Modular monolith: A single deployable Java application with internal modules per domain. This eliminates
network complexity and makes testing simpler, but does not solve the language heterogeneity problem
(Python AI service cannot be included) and does not allow independent scaling. Suitable for teams of two to
five engineers with a single-language stack; not suitable for the NeuralOps use case.

Serverless functions: Each event handler as an AWS Lambda or similar FaaS. Eliminates server management
entirely but introduces cold-start latency incompatible with the sub-50ms Kafka producer target, and requires
a cloud provider account — violating the zero-budget constraint.
