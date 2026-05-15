NeuralOps
=========

AI Agent Observability and Reliability Platform

NeuralOps is the infrastructure layer that enterprises need to run AI agents reliably in production. When
companies deploy LLM-powered agents — customer support bots, coding assistants, data analysis agents,
autonomous workflow orchestrators — those agents fail silently, cost unpredictably, and behave inconsistently.
NeuralOps solves this by providing real-time tracing of every agent decision, LLM call, tool invocation, and cost
event, giving engineering teams a Datadog-equivalent for their AI workloads.

System Requirements
-------------------

To run the full NeuralOps stack locally:

RAM: 16GB minimum, 32GB recommended. Ollama running llama3.1:8b plus all Docker containers consume
10-14GB combined under load. If your machine has less than 16GB, set the Docker Desktop memory limit to
12GB and expect reduced model performance.

CPU: 4 cores minimum, 8 cores recommended. Kafka, TimescaleDB, and Ollama inference are all CPU-intensive
under load.

Storage: 20GB free disk space for Docker images, PostgreSQL data volumes, and Ollama model weights.

OS: macOS, Linux, or Windows with WSL2. Native Windows without WSL2 is not supported.

Architecture
------------

NeuralOps is built as six independently deployable microservices:

The API Gateway (Spring Cloud Gateway) is the single entry point for all external traffic. It handles routing,
JWT validation, and rate limiting.

The Trace Ingestion Service receives trace events from AI agents via REST API and publishes them to Apache
Kafka with sub-50ms acknowledgment latency.

The Metrics Service consumes from Kafka, computes p50/p95/p99 latency, error rates, cost aggregates, and
throughput, and stores results in Redis for real-time access and TimescaleDB for historical queries.

The Alert Service evaluates threshold rules and anomaly signals, deduplicates alerts, and delivers notifications
via webhooks and Server-Sent Events.

The Cost Analytics Service tracks token consumption and estimated spend per agent and session, produces
cost breakdowns, and forecasts future spend using linear regression.

The User Service handles authentication, API key management, and team-based access control.

The AI Analysis Service (Python FastAPI) runs an Isolation Forest anomaly detection model on the trace
stream and publishes anomaly events back to Kafka.

The full architecture is documented in docs/architecture/system-design.md. The data flow from agent event
to dashboard display is documented in docs/architecture/data-flow.md.

Technology Stack
----------------

Backend: Java 21, Spring Boot 3.x, Spring Cloud Gateway, Spring Cloud Eureka
Event Streaming: Apache Kafka with ZooKeeper
Databases: PostgreSQL 16, TimescaleDB Community Edition
Cache: Redis 7
AI/ML: Python 3.11, FastAPI, scikit-learn (Isolation Forest), Ollama (llama3.1:8b, nomic-embed-text)
Frontend: React 18, TypeScript, Tailwind CSS, Recharts
Infrastructure: Docker, Docker Compose, Kubernetes, Helm
Observability: Prometheus, Grafana
Testing: JUnit 5, Mockito, Testcontainers, Cypress, k6
Build: Maven (Java), npm (frontend)

Local Development Setup
-----------------------

Prerequisites: Docker Desktop (with at least 12GB memory allocation), Java 21, Node.js 20, Maven 3.9+,
Python 3.11+.

Step 1: Clone the repository and navigate to the project root.

Step 2: Start all infrastructure services.

    docker compose up -d

Wait approximately 60 seconds for all services to initialize.

Step 3: Pull the required Ollama models. This only needs to run once.

    docker exec ollama ollama pull llama3.1:8b
    docker exec ollama ollama pull nomic-embed-text

llama3.1:8b is approximately 4.7GB. nomic-embed-text is approximately 270MB.

Step 4: Build all Java services.

    cd services
    mvn clean install -DskipTests

Step 5: Start the frontend.

    cd frontend
    npm install
    npm run dev

The frontend will be available at http://localhost:3000. The API gateway is at http://localhost:8080.
Grafana dashboards are at http://localhost:3001 (default credentials: admin/admin).

Service Port Reference
-----------------------

| Service                  | Port  |
|--------------------------|-------|
| API Gateway              | 8080  |
| Trace Ingestion Service  | 8081  |
| Metrics Service          | 8082  |
| Alert Service            | 8083  |
| Cost Analytics Service   | 8084  |
| User Service             | 8085  |
| AI Analysis Service      | 8090  |
| Frontend                 | 3000  |
| Grafana                  | 3001  |
| Prometheus               | 9090  |
| Kafka                    | 9092  |
| PostgreSQL               | 5432  |
| TimescaleDB              | 5433  |
| Redis                    | 6379  |
| Ollama                   | 11434 |

Features
--------

Real-time trace ingestion from any AI agent via REST API or SDK, with support for LLM calls, tool invocations,
decision points, and errors.

Sub-50ms Kafka ingestion acknowledgment at 1,000+ events per second per partition.

Latency percentile computation (p50, p95, p99) per agent, updated in real time via Redis.

Cost tracking per agent and session with linear regression forecasting and budget alerts.

AI-powered anomaly detection using Isolation Forest, running entirely locally via Ollama at zero cost.

Threshold-based alerting with configurable rules, webhook delivery, and deduplication.

Interactive React dashboard with real-time SSE updates, latency charts, cost analytics, and a searchable
trace explorer.

Full Kubernetes deployment with HorizontalPodAutoscaler and a production Helm chart.

Prometheus metrics from all services visualized in Grafana dashboards with alert thresholds.

Performance
-----------

Measured performance figures from load tests are documented in docs/PERFORMANCE.md. Target figures:
1,000+ traces/second ingestion throughput, p99 API latency under 200ms at 500 concurrent users, Kafka
consumer lag under 5 seconds at peak load, Redis cache hit ratio above 85% on metric queries.

Contributing
------------

See CONTRIBUTING.md for branch strategy, commit message format, and code review standards.

Architecture decisions are documented as ADRs in docs/decisions/.

License
-------

This project is proprietary. All rights reserved.
