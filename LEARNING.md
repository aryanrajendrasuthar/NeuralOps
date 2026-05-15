NeuralOps — Engineering Learning Journal
=========================================

This file is updated at the end of every sprint with in-depth explanations of the engineering concepts
encountered that sprint. The intended reader is a developer who understands basic programming but has not
yet worked in an industry engineering role. These are the things you would learn working alongside a senior
engineer — explained directly rather than buried in documentation or assumed as prior knowledge.

Sprint 6 — Kubernetes, Observability, and Production Readiness
---------------------------------------------------------------

What Kubernetes Actually Is and Why It Exists

Docker lets you package an application as a container. Running one container on one machine is
straightforward. The problem appears when you need to run fifty containers across ten machines,
route traffic between them, restart them when they crash, scale them up under load, and deploy
new versions without downtime. Doing this manually is a full-time job. Kubernetes automates it.

Kubernetes is a container orchestration system. You describe the desired state of your system
in YAML manifests — "I want 3 replicas of trace-ingestion-service, each with 2 CPUs and 1GB
of memory, reachable at port 8081" — and Kubernetes continuously works to make reality match
that description. If a pod crashes, Kubernetes restarts it. If a node fails, Kubernetes
reschedules those pods on other nodes. If you update the image tag, Kubernetes performs a
rolling update, replacing pods one at a time to ensure zero downtime.

The core primitives:

A Pod is the smallest deployable unit — one or more containers that share a network namespace
and can communicate via localhost. In NeuralOps, each service is one container per pod.

A Deployment manages a set of identical pods. It specifies the replica count, the container
image, resource limits, and health checks. When you change the image tag and apply the manifest,
the Deployment controller performs a rolling update automatically.

A Service is a stable network endpoint that load-balances traffic across the pods of a Deployment.
Services have a DNS name within the cluster — `metrics-service` resolves to the ClusterIP of the
metrics-service Service, which then routes to any healthy metrics-service pod. Services decouple
callers from the specific IPs of pods (which change every time a pod is restarted).

A ConfigMap holds non-secret configuration data as key-value pairs. An env var pointing to a
ConfigMap key (`valueFrom.configMapKeyRef`) injects that value into the container at runtime
without hardcoding it in the image.

A Secret holds sensitive data (passwords, tokens) base64-encoded. They are kept out of
ConfigMaps and container images, and can be encrypted at rest by cloud providers. The YAML
placeholder pattern in NeuralOps's 02-secrets.yaml is intentional — real secrets are
managed by a secrets manager (HashiCorp Vault, AWS Secrets Manager) and injected at deploy
time, never committed to version control.

What a HorizontalPodAutoscaler Does

A HorizontalPodAutoscaler (HPA) automatically adjusts the number of pod replicas based on
observed metrics. The NeuralOps HPA on trace-ingestion-service scales between 3 and 10
replicas when CPU utilization exceeds 70%. This is not instantaneous — the HPA polls metrics
from the Kubernetes Metrics Server every 15 seconds and applies a stabilization window of
3 minutes by default to prevent thrashing (scaling up and down rapidly during a traffic spike).

The practical consequence: if you receive a sudden spike that saturates all 3 replicas, you
will experience elevated latency for 2-5 minutes before the new replicas are scheduled,
running, and passing their readiness probes. This is called the cold start delay. Mitigation
strategies include setting the minimum replica count high enough to absorb expected peaks,
and pre-scaling before known high-traffic events.

What Helm Is and Why It Exists

Kubernetes manifests are YAML. They are exact — you specify every field for every resource.
When you have seven services plus their Services, HPAs, ConfigMaps, and Secrets, you have
dozens of files with a lot of repeated structure. Changing the namespace requires editing
every file. Changing the image tag for a release requires editing seven Deployment files.

Helm is a package manager for Kubernetes that introduces parameterized templates. A Helm
chart is a collection of templates plus a `values.yaml` file containing default values.
The templates use Go templating syntax (double braces) to insert values at render time.
`helm install neuralops ./infrastructure/helm/neuralops --set image.tag=v1.2.3` renders
all templates with the specified values and applies them to the cluster. `helm upgrade`
applies changes to an existing installation. `helm rollback` reverts to the previous release.

The key discipline with Helm: values.yaml is the contract between the chart author and the
chart user. Everything a user might need to customize should be a value. Everything that
should never change should be hardcoded in the template. The `_helpers.tpl` file defines
reusable template fragments — the NeuralOps `neuralops.labels` helper ensures every resource
gets the same set of standard Kubernetes labels, which is required for `kubectl get` queries
and for integration with monitoring tools that label-select resources.

How Prometheus Collects Metrics

Prometheus is a pull-based monitoring system. Rather than services pushing metrics to a
central collector, Prometheus scrapes an HTTP endpoint on each service at a configurable
interval. The Spring Boot Actuator exposes this endpoint at `/actuator/prometheus`. The
FastAPI ai-analysis-service exposes it at `/metrics` using the prometheus-client library.

Each scrape returns all registered metrics in the Prometheus exposition format: one metric
per line, with its value and any label key-value pairs that annotate it. Prometheus stores
these time-series values in its local TSDB (time-series database). The data is indexed by
metric name and labels. A query like `sum(rate(http_server_requests_seconds_count[5m]))
by (service)` computes the per-service request rate over the last 5 minutes.

PromQL (Prometheus Query Language) is the query language. The functions you need to know:

`rate(metric[window])` — the per-second rate of change of a counter over the window. Use
this for anything that only goes up (request count, error count).

`histogram_quantile(phi, rate(metric_bucket[window]))` — computes a percentile from a
histogram metric. Spring Boot Actuator automatically tracks request durations as histograms.
This is how you get p95 and p99 latency in Prometheus.

`absent(metric)` — returns 1 if the metric has no samples in the last scrape interval.
Used in the `TraceIngestionDown` alert to detect when a service disappears entirely.

What Grafana Does That Prometheus Cannot

Prometheus stores and queries metrics. Grafana visualizes them. Grafana connects to
Prometheus as a data source, then renders the query results as dashboards with panels
(line charts, bar charts, stat panels, tables, gauges). Multiple data sources can be
combined in a single dashboard — you can put Prometheus metrics and PostgreSQL query
results side by side.

The NeuralOps dashboard is provisioned automatically via the `dashboard-provider.yml`
file and the dashboard JSON. This means the dashboard exists from the first time
Grafana starts — engineers do not need to manually import it. Provisioned dashboards
can still be edited in the UI, but changes are reset if Grafana restarts unless the
JSON file is updated.

What Prometheus Alert Rules Are and How They Route to Engineers

A PrometheusRule (in a Kubernetes cluster using the Prometheus Operator) defines
alerting expressions alongside the metrics. When an expression evaluates to true for
longer than the `for` duration, Prometheus fires the alert to Alertmanager.
Alertmanager routes alerts to notification channels — Slack, PagerDuty, email —
based on label matchers. In NeuralOps, alerts are labeled with `team: platform`,
which Alertmanager uses to route to the platform engineering on-call rotation.

The `for` duration prevents flapping alerts. `GatewayHighP99Latency` fires only after
3 consecutive minutes of elevated latency. A brief spike — a single GC pause, a slow
database query — does not page anyone. This is intentional. Alert fatigue (too many
alerts) causes engineers to ignore alerts, which causes real incidents to be missed.
Tuning the `for` duration and the threshold is ongoing operational work.

What Load Testing Reveals That Unit Tests Cannot

Unit tests verify that code is correct given specific inputs. Load tests verify that
the system as a whole behaves correctly under the volume and concurrency of production
traffic. They reveal problems that only appear at scale:

Thread pool exhaustion: at low concurrency, every request gets a thread immediately.
At 500 concurrent users, requests queue waiting for a free thread. Spring Boot's
virtual threads (enabled for all NeuralOps services) eliminate this problem for most
workloads, but database connection pool exhaustion is still possible.

Lock contention in PostgreSQL: a single-row upsert for a session record is fast in
isolation. Under 500 concurrent requests for the same session ID, the database
acquires row-level locks and queues conflicting transactions. Load tests reveal this
as dramatically higher p99 latency than p95.

GC pressure: a JVM running at 2GB heap is fine at 100 req/s. At 1,000 req/s, the
rate of object allocation increases proportionally. Minor GC pauses become more
frequent. If the heap is not large enough, the JVM triggers major GC (stop-the-world),
causing multi-second pauses visible as sharp p99 latency spikes in the load test.

k6 is the industry standard tool for this kind of test. The `stages` configuration
ramps traffic up and down, simulating the traffic pattern of a real production day.
The `thresholds` configuration makes the test fail CI if the targets are not met.
The custom `handleSummary` function produces a readable output summarizing the result.

What an Operational Runbook Is and Why It Matters

A runbook is the written answer to "what do I do at 3am when this alert fires?" It
contains: what the alert means, how to diagnose the root cause step by step, what
the common causes are, and the commands to run to fix each one.

Runbooks exist because even experienced engineers cannot reliably recall diagnostic
steps when woken from sleep by a pager. They also transfer knowledge from the engineers
who built a system to the engineers who are on-call for it. The engineer who built
the HikariCP connection pool knows immediately what "HikariPool timeout" means. An
on-call engineer who has never seen that log line does not.

Good runbooks are updated after every incident. If you resolve a problem by running
a command not in the runbook, you add it to the runbook before you go back to sleep.
This is the most important discipline in SRE (Site Reliability Engineering): treating
operational knowledge as a first-class artifact that lives alongside the code.

The NeuralOps runbooks cover the two most likely production alerts: high gateway
latency (usually a downstream bottleneck) and Kafka consumer lag (usually a database
write slowdown or a crashing consumer). These two patterns cover the majority of
production incidents in event-driven microservices systems.

Sprint 5 — Authentication, Authorization, and the Frontend
-----------------------------------------------------------

What JWT Authentication Actually Is

A JSON Web Token (JWT) is a signed, compact, URL-safe string that asserts claims about a subject. The
standard format is three Base64URL-encoded segments separated by periods: header.payload.signature.

The header names the signing algorithm (HS256, RS256). The payload contains claims — `sub` (the subject,
typically a user ID), `iat` (issued at), `exp` (expiry), and any custom claims your application needs
(in NeuralOps: `email`, `role`, `type`). The signature is a cryptographic MAC produced by the server
using a secret key. No one can forge a token without that key, and anyone who has the key can verify any
token without a database lookup.

This is the critical insight: JWT is stateless. The user-service does not need to store sessions in a
database. Any service can validate a JWT by checking the signature against the shared secret. This is
why JWTs are well-suited to microservices — the gateway can validate a token once, and downstream
services can trust the validated principal without their own database calls.

The trade-off is that tokens cannot be revoked before they expire. This is why NeuralOps uses short-lived
access tokens (15 minutes). If a token is stolen, it can be used for at most 15 minutes. The refresh
token (30 days) lives in Redis, so logging out revokes it by storing the token ID in a blocklist.
When the client exchanges the refresh token for a new access token, the server checks Redis first.

Access tokens vs. refresh tokens: access tokens are sent with every request in the Authorization header.
Refresh tokens are sent once, to the /auth/refresh endpoint, in exchange for a new access token. They
are never sent to any other endpoint. This separation limits the window of exposure — even if an access
token is intercepted, it expires in 15 minutes. The refresh token is longer-lived but is only ever
sent to one endpoint over TLS.

How bcrypt Password Hashing Works

bcrypt is a password hashing algorithm designed in 1999 specifically for storing passwords. It has two
properties that make it the right choice over general-purpose hash functions like SHA-256 or MD5.

First, bcrypt is slow by design. The cost factor (10 in NeuralOps) controls the number of rounds of
key expansion. At cost 10, hashing one password takes roughly 100ms on a modern CPU. This is
irrelevant for login (users do not notice 100ms of hashing) but is decisive against attackers: an
attacker who steals the database can only attempt 10 hashes per second per CPU core, making
brute-force attacks computationally prohibitive. MD5 can hash billions of passwords per second.

Second, bcrypt automatically generates and incorporates a per-password salt. A salt is random bytes
mixed into the input before hashing. This means two users with the same password produce different
hashes, defeating rainbow table attacks (precomputed hash-to-password mappings).

In Spring Security, `BCryptPasswordEncoder` handles all of this. `encode(rawPassword)` returns the
full bcrypt string including the algorithm version, cost factor, and salt — all encoded as a single
string. `matches(rawPassword, encodedHash)` re-hashes the input using the salt embedded in the stored
hash and compares in constant time, preventing timing attacks.

What CORS Is and Why It Exists

The Same-Origin Policy is a browser security mechanism that prevents JavaScript on one origin
(domain + protocol + port) from reading responses from a different origin. When the NeuralOps
frontend at `localhost:3000` makes an API request to `localhost:8080`, the browser blocks it by
default because the ports differ — they are different origins.

CORS (Cross-Origin Resource Sharing) is the mechanism by which servers tell browsers which origins
they trust. The browser sends a preflight request (`OPTIONS`) to the server asking: "may this
JavaScript make this request?" The server responds with `Access-Control-Allow-Origin` and
`Access-Control-Allow-Methods` headers. If the response permits the origin, the browser proceeds.
If not, the browser blocks the request and the JavaScript code receives an error.

This happens in the browser only. The CORS restriction does not apply to server-to-server requests,
curl, Postman, or any non-browser HTTP client. This is why developers are sometimes confused when
a request works from their terminal but fails in the browser.

In NeuralOps, CORS is configured on the gateway (port 8080), which is the single entry point for
all browser traffic. Each downstream service has its own CORS configuration as well, which is
correct defense-in-depth — if a service is ever exposed directly, it is still protected.

How React Query Manages Server State

React Query is a server state management library. It is important to understand what "server state"
means as distinct from "client state." Client state is data that lives entirely in the browser —
form inputs, modal open/closed, selected tab. Server state is data that lives on the server and is
fetched by the client — metrics, alert rules, agent status.

Without React Query, managing server state requires useEffect and useState with careful coordination
of loading, error, and success states, manual cache invalidation, and re-fetching logic. It is
verbose and error-prone. React Query replaces all of this with three primary concepts.

A query is a declarative fetch of data identified by a query key. `useQuery({ queryKey: ['metrics',
'latency', agentId], queryFn: () => metricsApi.getLatency(agentId) })` fetches latency data and
returns `{ data, isLoading, isError }`. React Query caches the result under the key. If the same
query key is used in two components, only one network request is made.

`staleTime` controls how long cached data is considered fresh. With `staleTime: 30_000`, a query
that was fetched 25 seconds ago will not be re-fetched when a component mounts — the cached value
is used. With `refetchInterval: 15_000`, the query refreshes every 15 seconds in the background,
keeping the dashboard live.

A mutation is an operation that modifies server state — creating an alert rule, deleting one,
toggling enabled. After a mutation succeeds, `queryClient.invalidateQueries({ queryKey: ['alerts',
'rules'] })` marks the cached rules as stale, which triggers an immediate refetch. This is how
the rules list updates immediately after you create or delete a rule.

Why API Keys Are Better Than Passwords for Machine Access

When a machine client (a CI pipeline, another microservice, a script) needs to authenticate with an
API, you do not want it to use a username and password. Passwords have several problems in this
context: they are long-lived secrets that are hard to rotate without downtime, they give access to
the full account including the ability to change the password and lock out the real user, and they
are not auditable — you cannot tell which password access came from which system.

API keys solve these problems. Each key has a descriptive name ("production deployment pipeline",
"analytics export script") so you know where it is used. Keys can be revoked individually — if one
system is compromised, you revoke that key and issue a new one without affecting any other system.
Keys can be scoped to specific capabilities (a read-only key for a monitoring script, a write key
for the ingestion service).

The raw key is shown exactly once — at creation — and never stored. NeuralOps stores a bcrypt hash
of the key, just like a password. When a request arrives with the key, the server finds the matching
key record by the key prefix (the first 12 characters, which are not secret) and then bcrypt-verifies
the full key against the stored hash. This means a database breach does not expose any valid keys.

The prefix (stored in plaintext in the `key_prefix` column) lets users identify which key is which
in the management UI without the server needing to store or display the actual key value.

How the Frontend State Machine Works

Every user interface is implicitly a state machine. The NeuralOps frontend has three authentication
states: unauthenticated (no tokens in localStorage), authenticated (valid tokens in localStorage),
and refreshing (the access token expired, the refresh token is being exchanged for a new one).

These states map directly to navigation. Unauthenticated users are redirected to `/login` by the
`Layout` component, which reads `isAuthenticated` from `AuthContext`. Authenticated users see the
dashboard. When the Axios interceptor detects a 401, it triggers the refresh flow atomically — the
original request is queued while the refresh completes and retried with the new token.

The `AuthProvider` wraps the entire application and makes `user`, `login`, and `logout` available
via `useAuth()`. This is the React Context pattern: a single source of truth for a shared state
that many components need to read. The alternative — prop drilling — would require passing user
down through every component in the tree, which is unmaintainable.

One important implementation detail: the user object is stored in localStorage on login and read
back on page load. This means the authentication state survives browser refresh — the user does not
have to log in again every time they open a new tab. The risk is that if the access token expires
and the refresh token is also invalid (user logged out on another device, or the 30-day window
passed), the page will silently redirect to `/login` on the first API call, which is correct behavior.

Sprint 3 — Metrics Engine and Real-Time Analytics
---------------------------------------------------

What Latency Percentiles Actually Mean

When someone says "our average latency is 120ms," they are giving you almost no useful information. The
average is dominated by the common case and completely hides the worst case. If 95% of requests take 100ms
and 5% take 3000ms, the average is around 245ms — a number that accurately describes no actual request.

Latency percentiles tell you what fraction of requests are slower than a given threshold. The p50 (median)
is the midpoint — half of all requests are faster, half are slower. The p95 is the latency value that 95% of
requests complete within. The p99 is the value that 99% of requests complete within.

Why do engineers focus on p95 and p99 rather than the average? Because the tail latency is what users
actually experience when things go wrong. A payment system might handle 10,000 requests per second.
At p99 = 2 seconds, that means 100 requests per second are taking more than 2 seconds — likely causing
timeouts, retries, and cascading failures. A mean of 120ms would completely mask this.

SLAs (Service Level Agreements) are always written in percentile terms: "p99 latency must be under 500ms
for 99.9% of production traffic." This is the language you will encounter in every production engineering
role. When a team says "we have a latency problem," they mean their p95 or p99 has blown past their SLA
threshold, not that the average increased slightly.

NeuralOps computes latency percentiles using Redis sorted sets. Each trace event is inserted with a score
equal to its latency in milliseconds. The p50, p95, and p99 are retrieved by rank — the element at rank
(count * 0.50) is the p50, and so on. This is O(log n) per percentile read and O(log n) per write, which
is why Redis sorted sets are the canonical choice for real-time latency percentile tracking. Computing
percentiles by scanning all rows in PostgreSQL would be correct but far too slow for a live dashboard.

How Redis Data Structures Work

Redis is not just a cache. It is a data structure server. The choice of data structure is critical to
correctness and performance:

A Hash (`HSET`, `HGET`, `HINCRBY`) is a flat map of string fields to string values within a single key.
NeuralOps uses it to store per-agent scalar accumulators: `agent:stats:{agentId}` holds `trace_count`,
`token_count`, `error_count`, and `cost_usd_total`. The `HINCRBY` command atomically increments an integer
field — no read-modify-write cycle, no race condition, no lost updates. This is the correct pattern for
counters that multiple consumers might write concurrently.

A Sorted Set (`ZADD`, `ZRANGE`, `ZRANK`, `ZCARD`) stores unique members each with an associated floating-
point score. Members are ordered by score ascending. NeuralOps uses sorted sets to maintain the sliding
window of latency values, where the score is the latency in milliseconds. Rank-based retrieval of the p50,
p95, and p99 is a single `ZRANGE` call with a computed rank index.

A String (`SET`, `GET`) is the simplest type — a single value per key. NeuralOps uses it for the
`agent:lastseen:{agentId}` key, which stores the timestamp of the most recent trace event. It is set with
`SET key value` in a pipeline with no expiry, relying on the global 24-hour TTL applied after pipeline
execution.

All four write operations in `recordTraceEvent()` are executed in a single pipeline — the client batches
all commands and sends them in one TCP round trip. Without pipelining, four separate Redis commands would
require four round trips. At 1,000 traces per second, that is the difference between 4ms of Redis latency
overhead and 0.5ms.

What is a Circuit Breaker

A circuit breaker is a safety mechanism that stops calling a downstream service when that service is
failing, and gives it time to recover before retrying. The name comes from electrical circuit breakers,
which interrupt current when a circuit is overloaded.

Without a circuit breaker, if Redis goes down, every incoming trace event will attempt a Redis write, wait
for the timeout (often 5-10 seconds), log an error, and fail. At 1,000 requests per second, this creates
a backlog of thousands of blocked threads before the first timeout even completes. The thread pool
exhausts. The service stops accepting new requests. The ingestion pipeline backs up. A Redis outage becomes
a full system outage.

With a circuit breaker (we use Resilience4j), the breaker monitors recent failures. After 50% of the last
10 calls fail, the breaker opens. Subsequent calls immediately throw an exception without attempting the
network call. This is called failing fast. After a wait period (15 seconds for Redis, 30 seconds for
TimescaleDB in NeuralOps), the breaker enters a half-open state and allows a single test call. If it
succeeds, the breaker closes and normal operation resumes. If it fails, the breaker re-opens.

The important engineering insight: a circuit breaker does not fix the downstream failure. It limits the
blast radius. It prevents one failing dependency from taking down the entire service. Callers that get
"circuit breaker open" errors should handle them gracefully — in NeuralOps, the metrics-service logs the
error and still acknowledges the Kafka offset, accepting a brief gap in real-time metrics rather than
blocking event processing.

What is HikariCP and Why Connection Pool Sizing Matters

Every time a Java service queries PostgreSQL, it needs a database connection. Opening a new TCP connection
and completing the PostgreSQL authentication handshake takes 50-100ms. For a service handling hundreds of
requests per second, creating a new connection per request would immediately saturate both the Java process
and the PostgreSQL server.

A connection pool maintains a set of pre-opened connections and hands them out to callers. HikariCP is the
fastest connection pool available for Java — it is the default in Spring Boot. When a request needs a
database connection, it borrows one from the pool. When it is done, it returns the connection, and the
next request can reuse it without the overhead of connection setup.

The `maximum-pool-size` setting (20 in NeuralOps) controls the maximum number of simultaneous database
connections per JVM. This is one of the most consequential configuration values in any production service.
Too low: requests queue waiting for a connection when all 20 are in use, adding latency. Too high:
PostgreSQL's per-connection overhead (roughly 5-10MB of RAM per connection plus locking overhead) causes
the database to thrash under load.

The correct maximum pool size is not "as high as possible." It is the smallest number that keeps the
connection wait queue empty under peak load. For most OLTP workloads, 10-20 connections per service
instance is correct. With three service instances, that is 30-60 PostgreSQL connections total — a number
any PostgreSQL instance can handle comfortably.

What Exactly-Once Kafka Semantics Means

Kafka offers three delivery guarantees: at-most-once (messages may be lost), at-least-once (messages may
be delivered more than once), and exactly-once (each message is processed exactly once).

In practice, exactly-once end-to-end is achieved by combining two things: idempotent producers (so the
same message is never published twice, even if retried) and transactional consumers with MANUAL_IMMEDIATE
acknowledgment (so the offset is only committed after successful processing).

NeuralOps uses MANUAL_IMMEDIATE acknowledgment in all Kafka consumers. The offset is committed only after
the database write succeeds. If the service crashes between receiving the message and committing the
offset, the message will be redelivered when the service restarts. This is at-least-once delivery —
messages can be processed more than once.

To handle redelivery safely, NeuralOps uses idempotent database operations. The cost-analytics-service
uses `INSERT ... ON CONFLICT DO UPDATE` (an upsert) rather than a plain `INSERT`. If the same event is
processed twice, the upsert either creates the row the first time or updates the accumulator atomically
the second time, producing the same result either way. This is idempotent processing.

True exactly-once requires the consumer to atomically commit the database write and the Kafka offset in
the same transaction, which requires Kafka's transactional API and is significantly more complex. For
NeuralOps, at-least-once delivery with idempotent writes is the correct engineering trade-off: simpler,
faster, and sufficient for an observability use case where a double-counted metric is far less harmful
than a missed metric.

What is Linear Regression and How It Applies to Cost Forecasting

Linear regression is a statistical method that fits a straight line through a set of data points,
minimizing the sum of squared distances from each point to the line. The result is a line defined by a
slope (how much y changes per unit of x) and an intercept (the value of y when x is zero). Once you have
this line, you can predict y for any future x.

For cost forecasting in NeuralOps, the x-axis is the day index (0, 1, 2, ... 29 for the last 30 days)
and the y-axis is the cost in USD on that day. If an agent's costs are growing linearly — say, $0.10 per
day last week and $0.15 per day this week — the regression line will have a positive slope and will
correctly predict that costs will continue rising. If costs are flat, the slope will be near zero.

The R-squared value (R²) measures how well the line fits the data. An R² of 1.0 means the data lies
perfectly on the regression line — the model explains 100% of the variance. An R² of 0.0 means the data
has no linear trend at all — the model is no better than predicting the average every day. In production,
an R² below 0.5 is a signal that the linear model is a poor fit and the forecast should be interpreted
cautiously.

We use Apache Commons Math `SimpleRegression` for the computation. It accepts (x, y) data points,
internally computes the least-squares line, and exposes `predict(x)` for future values and `getRSquare()`
for model quality. The library handles all the numerical linear algebra — you do not need to implement the
algorithm yourself.

A critical production safeguard: any predicted negative cost is clamped to zero. Linear regression makes
no assumptions about the physical meaning of its output. If costs were high two weeks ago and fell to
zero last week, the regression line might predict negative costs next week. That is mathematically
coherent but economically nonsensical. Always validate model outputs against physical constraints.

What Flyway Migrations Are and Why They Matter in Production

Flyway is a database schema migration tool. It maintains a table in your database (`flyway_schema_history`)
that records every migration that has ever been applied, along with a checksum. When the application
starts, Flyway compares the migrations on the classpath with the ones in the history table and applies
any that have not been run yet.

This gives you two critical guarantees. First, the schema is always in sync with the application code.
If you deploy a new version of the service that expects a column that does not exist yet, Flyway adds the
column during startup before the application begins serving traffic. Second, every schema change is
version-controlled. The migration history is part of the git history, so you can trace exactly when any
schema change was made and why.

The `baseline-on-migrate: true` setting tells Flyway to treat an existing database as baseline v0 if the
history table does not exist. This is how you adopt Flyway on an existing database without running all
migrations from scratch.

The most important production rule for Flyway migrations: once a migration has been applied to any
production database, it is immutable. You cannot edit V1__create_tables.sql after it has run — Flyway
stores the checksum and will fail to start if it detects a change. If you need to alter the schema,
you write a new migration file (V2__add_column.sql). This constraint is actually a safety feature: it
prevents you from silently changing a migration that has already run in one environment but not another.

What 99.9% Uptime Actually Means

99.9% uptime ("three nines") means the service is allowed to be unavailable for 8.76 hours per year, or
43.8 minutes per month. 99.99% ("four nines") is 52.6 minutes per year. These numbers sound large but
they include planned maintenance windows, deployments, and unexpected failures.

Every engineering team at a production company has an SLA. The SLA is a commitment to users and
stakeholders. Breaching it triggers an incident review. At larger companies, breaching it repeatedly may
trigger financial penalties in customer contracts or trigger automatic escalation to on-call engineering
managers.

The path to high uptime is not writing perfect code — it is designing the system so that individual
component failures do not cause full outages. The circuit breakers on Redis and PostgreSQL mean a Redis
outage degrades NeuralOps (metrics are not computed in real-time) rather than taking it down entirely.
The Kafka consumer's MANUAL_IMMEDIATE acknowledgment means a transient database error causes a delay in
processing but no data loss. The event-driven architecture means the ingestion service can continue
accepting events even while the metrics service is offline, because the events are buffered in Kafka.

This design philosophy — preferring degraded operation over full failure — is what distinguishes production
systems from prototype systems. You do not need to solve every failure mode. You need to ensure that the
most likely failure modes produce graceful degradation rather than total outage.

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
