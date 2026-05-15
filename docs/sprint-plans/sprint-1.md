Sprint 1 — Foundation and Architecture
========================================

Duration: Day 1, approximately 3 hours
Goal: Establish the complete project skeleton, all documentation, architecture decisions, and core infrastructure
so that every subsequent sprint builds on a solid, well-understood foundation.

What You Will Learn
-------------------

By the end of this sprint you will understand what a microservices architecture is and why companies use it
instead of building one large application. You will understand what Apache Kafka is and why event streaming is
the right communication pattern for high-throughput data pipelines. You will understand what Docker Compose
is, how it works, and how engineering teams use it to run complex multi-service environments locally without
installing every dependency on their laptops. You will understand what an Architecture Decision Record is and
why writing down the reasoning behind technical decisions is a professional engineering practice. You will also
understand what a senior engineer's first day on a new project actually looks like.

Deliverables
------------

All documentation files for the architecture, data flow, and architecture decision records.

Sprint plans for all six sprints.

Docker Compose file with Kafka, ZooKeeper, Redis, PostgreSQL, TimescaleDB, Prometheus, Grafana, and Ollama.

Maven parent pom.xml and all child pom.xml files for the five Java services.

Frontend package.json and Vite configuration for the React application.

Base Spring Boot Application classes for all five Java services — empty but compilable and runnable with
working health check endpoints.

FastAPI base for the Python AI analysis service with a working health check endpoint.

LEARNING.md with the Sprint 1 teaching section.

README.md with full project overview, setup instructions, and architecture summary.

CONTRIBUTING.md with branch strategy, commit conventions, and code review standards.

Commit Checkpoints
------------------

CHECKPOINT 1A: After all documentation files and project structure are created.
Suggested commit message: "docs: add system architecture documentation and project structure"

CHECKPOINT 1B: After Docker Compose and all infrastructure configuration files.
Suggested commit message: "infra: add docker-compose with kafka, postgres, redis, timescaledb, prometheus, grafana"

CHECKPOINT 1C: After all service skeletons, Maven poms, and base configurations.
Suggested commit message: "feat: initialize all microservice skeletons with base spring boot configuration"

Acceptance Criteria
-------------------

Running `docker compose up -d` starts all infrastructure services without errors.

Running `mvn clean install -DskipTests` from the services/ directory compiles all Java modules without errors.

All five Java services start and their /actuator/health endpoints return {"status":"UP"}.

The FastAPI service starts and GET /health returns {"status":"ok"}.

No hardcoded values in any configuration file — all environment-specific values use environment variable
references.
