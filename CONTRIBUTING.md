Contributing to NeuralOps
=========================

This document defines the engineering workflow for everyone working on NeuralOps. Follow these conventions
without exception. Consistency in a codebase is more valuable than any individual's stylistic preferences.

Branch Strategy
---------------

The main branch is always deployable. No one pushes directly to main. Every change goes through a pull
request with at least one reviewer approval.

Branch naming follows the pattern: type/short-description

    feat/trace-ingestion-kafka-producer
    fix/alert-deduplication-race-condition
    refactor/metrics-redis-pipeline
    docs/adr-004-timescaledb-hypertables
    test/trace-ingestion-integration

The type prefix must be one of: feat, fix, refactor, docs, test, chore, infra. The description uses hyphens,
not underscores, and is limited to five words or fewer.

Branches are created from main and merged back to main. Long-lived feature branches are a sign that a
change is too large — split it into smaller pieces that can each be reviewed and merged independently.

Commit Message Format
---------------------

NeuralOps uses Conventional Commits (conventionalcommits.org). Every commit message must follow this
format:

    type(scope): short description in present tense

    Optional longer body explaining the why, not the what. The what is
    visible in the diff. The why is not.

    Optional footer: references, breaking change notices.

The type must be one of:

    feat        A new feature visible to users or callers of the service
    fix         A bug fix
    refactor    Code change that is neither a feature nor a bug fix
    test        Adding or modifying tests
    docs        Documentation changes only
    infra       Infrastructure, Docker, Kubernetes, CI configuration
    chore       Dependency updates, build configuration, tooling

The scope is the service or component affected: trace-ingestion, metrics, alerting, cost-analytics, auth,
frontend, gateway, ai-analysis, kafka, postgres, docker.

The short description is lowercase, present tense, no period at the end, maximum 72 characters including
the type and scope prefix.

Examples of correct commit messages:

    feat(trace-ingestion): add kafka producer with acks-all durability
    fix(alerting): correct deduplication window calculation for UTC midnight
    test(metrics): add integration test for redis latency histogram update
    infra: add timescaledb continuous aggregate for hourly cost rollup
    docs(adr): add ADR-004 for timescaledb over raw postgresql time-series

Examples of incorrect commit messages:

    Fixed the bug                               (no type, no scope, past tense)
    feat: Updated alert service to fix issue    (past tense, vague)
    WIP                                         (meaningless)
    feat(alerting): Fixed dedup window          (past tense, past tense with capital)

Code Review Standards
---------------------

Every pull request requires at least one approval before merging. The author is responsible for addressing all
review comments, not the reviewer. If you disagree with a comment, discuss it — do not ignore it.

Reviewers check for: correctness of the implementation against the stated intent, test coverage for new code,
security implications (SQL injection, authentication bypass, secret exposure), performance implications for
any code on the hot path (trace ingestion, Kafka consumers, Redis operations), and adherence to the coding
standards below.

Reviews are not a place to enforce stylistic preferences. If a piece of code is correct, tested, and readable,
approve it even if you would have written it differently. Reserve change requests for actual problems.

Coding Standards
----------------

Java: All services use Java 21. No raw types, no unchecked casts, no catching Exception or Throwable
directly. Use Optional instead of returning null from methods. Use records for immutable data carriers
where appropriate. All classes that represent domain entities must have equals, hashCode, and toString
defined explicitly or derived from records or Lombok @Value.

Configuration: No hardcoded values in Java source code. All environment-specific configuration lives in
application.yml with environment variable overrides using the ${VARIABLE_NAME:default} syntax. Secrets
(database passwords, JWT signing keys) must never appear in source code or committed configuration files.
Use Docker Compose environment files or Kubernetes Secrets.

Logging: Use SLF4J with the @Slf4j Lombok annotation. Log at INFO for significant lifecycle events (service
started, Kafka consumer group joined, circuit breaker tripped). Log at DEBUG for per-request detail that is
too verbose for production but useful for debugging. Log at WARN for recoverable errors. Log at ERROR only
for conditions that require human attention. Never log sensitive data (passwords, tokens, PII).

Error handling: Services that expose HTTP APIs must return RFC 7807 Problem Details format for all 4xx
and 5xx responses. Never return a stack trace to an API caller. Log the full exception server-side.

Testing: Every new feature requires unit tests. Every integration point (Kafka, PostgreSQL, Redis, external
HTTP) requires at least one integration test using Testcontainers. Tests must be deterministic — no sleeps,
no reliance on wall clock, no shared mutable state between tests.

Performance: Any code added to the trace ingestion hot path must be benchmarked with JMH before merging
if there is any reason to believe it could affect throughput. Database queries in performance-sensitive code
must include an EXPLAIN ANALYZE result in the PR description showing no sequential scans on large tables.

Documentation
-------------

All public API endpoints must have OpenAPI annotations. All Architecture Decision Records must be written
before the implementation is merged. All runbooks must be updated if an operational procedure changes.

No placeholder content. Every document must be complete and accurate before it is committed. "TODO: fill
this in later" is not acceptable in any document in this repository.
