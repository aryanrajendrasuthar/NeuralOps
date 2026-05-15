Sprint 6 — Production Hardening, Testing, and Documentation
=============================================================

Duration: Day 6, approximately 3 hours
Goal: Take everything built and make it production-ready. This sprint is what separates a demo from a
deployable product.

What You Will Learn
-------------------

By the end of this sprint you will understand what Kubernetes is and how it differs from Docker Compose. You
will understand what a Helm chart is and why teams use it to package Kubernetes applications. You will
understand what rate limiting is and why public APIs need it. You will understand what a runbook is and why
on-call engineers need operational documentation. You will understand what a load test is, how to run one with
k6, and what the output means. You will understand what production-ready actually means at a company like
Google or Amazon — and why most software that works on a laptop is not production-ready without additional
engineering.

Deliverables
------------

Kubernetes manifests for all services: Deployments, Services, ConfigMaps, Secrets, HorizontalPodAutoscaler.

Helm chart for the full NeuralOps stack.

Prometheus metrics exposed from all Java services via Spring Boot Actuator and Micrometer.

Grafana dashboards for NeuralOps internal health: service latency, Kafka consumer lag, database connection pool.

Rate limiting on all public APIs via Spring Cloud Gateway and Redis.

API versioning strategy implemented.

Full E2E test suite covering agent registration, trace ingestion, metric computation, and alert firing.

Load test script using k6 demonstrating the system handles 1,000 traces per second.

docs/runbooks/incident-response.md covering all major failure modes.

docs/runbooks/deployment.md with step-by-step Kubernetes deployment instructions.

docs/api/ complete API reference for all services.

docs/PERFORMANCE.md with measured load test results, test conditions, and k6 output.

Final README.md polish with architecture diagram, quick start guide, and contributing guide.

LEARNING.md updated with Sprint 6 teaching section.

Commit Checkpoints
------------------

CHECKPOINT 6A: After Kubernetes manifests and Helm chart.
Suggested commit message: "infra: add kubernetes manifests and helm chart for production deployment"

CHECKPOINT 6B: After Prometheus metrics, Grafana dashboards, and rate limiting.
Suggested commit message: "feat(observability): add prometheus metrics, grafana dashboards, and api rate limiting"

CHECKPOINT 6C: After all tests, load tests, runbooks, and final documentation.
Suggested commit message: "docs: add runbooks, api reference, load tests, and final documentation polish"

Acceptance Criteria
-------------------

`helm install neuralops ./infrastructure/kubernetes/helm/neuralops` completes without errors against a local
Minikube cluster.

All Kubernetes Deployments reach Running state with correct readiness probe responses.

Prometheus scrapes metrics from all services and all required metric names are present.

The k6 load test sustains 1,000 traces per second for 5 minutes with Kafka consumer lag under 5 seconds.

The Grafana dashboard shows all required metrics with configured alert thresholds.

Rate limiting returns 429 when a single API key exceeds the configured request-per-second limit.

The E2E test suite passes completely against a running Docker Compose stack.
