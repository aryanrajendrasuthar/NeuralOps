Sprint 5 — Frontend Dashboard and User Management
===================================================

Duration: Day 5, approximately 3 hours
Goal: Build the production-grade React dashboard that makes all backend data visible and actionable for
engineering teams managing AI agents in production.

What You Will Learn
-------------------

By the end of this sprint you will understand how JWT authentication and refresh token rotation work in practice.
You will understand the difference between Server-Sent Events and WebSockets and when to use each. You will
understand how to structure a large React application with TypeScript. You will understand why engineering teams
use TypeScript over plain JavaScript. You will understand how to design dashboards that engineers actually use —
information hierarchy, data density, and the difference between a useful dashboard and a dashboard that looks
impressive but tells you nothing actionable.

Deliverables
------------

user-service: JWT authentication with refresh token rotation, user registration, team management, and API key
lifecycle management for agent SDKs.

Frontend React 18 application with TypeScript and Tailwind CSS.

Pages: Login/Register, Dashboard Overview, Agent Detail View, Trace Explorer, Cost Analytics, Alerts Center,
Settings.

Dashboard Overview: live metrics cards (active agents, total traces today, p95 latency, total cost today), agent
health status grid, recent anomalies feed.

Agent Detail View: latency time-series chart, cost over time chart, error rate gauge, recent traces table with
filtering.

Trace Explorer: searchable and filterable table of all traces with a detail drawer showing the full trace payload.

Cost Analytics: cost breakdown by agent, cost trend chart, budget progress bars.

Alerts Center: active alerts list, alert history, alert rule configuration.

Real-time updates via Server-Sent Events from the backend.

LEARNING.md updated with Sprint 5 teaching section.

Commit Checkpoints
------------------

CHECKPOINT 5A: After user-service and JWT authentication.
Suggested commit message: "feat(auth): implement jwt authentication with refresh token rotation and api key management"

CHECKPOINT 5B: After core dashboard pages.
Suggested commit message: "feat(frontend): add dashboard overview, agent detail, and trace explorer pages"

CHECKPOINT 5C: After cost analytics, alerts UI, and SSE real-time updates.
Suggested commit message: "feat(frontend): add cost analytics dashboard, alerts center, and real-time sse updates"

Acceptance Criteria
-------------------

Login with valid credentials returns access and refresh tokens. Refresh token rotation produces a new pair.

Dashboard Overview loads in under 1 second with real data.

SSE stream delivers metric updates within 2 seconds of a trace event being ingested.

Trace Explorer full-text filter narrows results without a full page reload.

Alert rule configuration UI saves a new threshold rule that subsequently fires when the condition is met.
