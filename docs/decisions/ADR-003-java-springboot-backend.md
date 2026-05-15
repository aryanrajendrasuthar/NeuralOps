ADR-003: Java 21 and Spring Boot 3.x for Backend Services
==========================================================

Date: 2025-05-14
Status: Accepted
Deciders: Platform Engineering Team

Context
-------

Five of the six NeuralOps microservices require a backend framework for REST API hosting, Kafka integration,
database access, security, and configuration management. The AI analysis service is Python and is handled
separately. For the five Java services, we needed to select a language and framework.

The candidates considered were Java 21 with Spring Boot 3.x, Go with the standard library and a minimal
framework, and Node.js with NestJS.

Decision
--------

We chose Java 21 with Spring Boot 3.x.

Rationale
---------

Spring Boot has the most mature ecosystem for the specific combination of features NeuralOps requires:
Kafka integration (Spring for Apache Kafka), relational database access with connection pooling (Spring Data
JPA + HikariCP), JWT-based security (Spring Security with OAuth2 Resource Server), schema migration
(Flyway integrates directly into Spring Boot auto-configuration), service discovery (Spring Cloud Eureka), API
gateway (Spring Cloud Gateway), circuit breakers (Resilience4j Spring Boot starter), metrics exposition
(Micrometer + Actuator with Prometheus registry), and OpenAPI documentation (SpringDoc). All of these are
first-class Spring Boot starters — they require configuration, not custom integration code.

Java 21 adds virtual threads (Project Loom) via `spring.threads.virtual.enabled=true`. For a service that handles
many concurrent HTTP connections where most time is spent waiting on I/O (Kafka, database, Redis), virtual
threads allow handling thousands of concurrent requests with a small carrier thread pool. This is directly
relevant to the 500 concurrent request throughput target.

Spring Boot 3.x requires Java 17 as minimum and adds native compilation support via GraalVM. While we do
not use GraalVM native images in Sprint 1, the architecture is compatible — this option remains open for
production optimization.

Maven is the build tool. It was chosen over Gradle because Maven's deterministic dependency resolution and
widely understood XML format are more accessible for developers new to the Java ecosystem. Gradle's Groovy
or Kotlin DSL adds a learning curve that is not justified for this project.

Consequences
------------

Startup time: Spring Boot applications have slower cold-start times than Go or Node.js. This matters for
Kubernetes rolling deployments and horizontal scaling events. We mitigate this with Spring Boot's layered JAR
format (improved Docker layer caching) and by configuring readiness probes with appropriate delay settings
so that traffic is not routed to a service until its application context has fully initialized.

Memory footprint: The JVM has higher baseline memory consumption than Go. Each service is configured with
appropriate JVM heap limits in Kubernetes resource requests. Under steady-state load, each Spring Boot service
runs comfortably within 512MB-768MB heap.

Rejected Alternatives
---------------------

Go: Excellent performance characteristics and low memory footprint, but the ecosystem for Kafka, JPA-style
database access, and Spring Security-equivalent auth flows is fragmented across multiple non-standard
libraries. Building the equivalent of Spring Boot's auto-configuration from individual Go libraries would require
significant custom integration work that is not the purpose of this project.

Node.js with NestJS: Viable for API services but not well-suited for Kafka consumer workloads that require
careful thread management. Node's single-threaded event loop is a poor fit for CPU-bound tasks like metric
aggregation. NestJS would introduce a second language alongside Python (the AI service), increasing the
cognitive overhead of context switching.
