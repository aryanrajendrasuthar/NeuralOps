-- V3: Create traces table
-- The primary event store. Each row is one trace event from one agent.
-- Indexed for the two primary query patterns: by agent+time, and by session+time.
-- The payload column is omitted here intentionally — large JSON payloads are kept in
-- the Kafka stream only to avoid bloating the relational store. Only structured fields
-- needed for queries are stored in columns.

CREATE TABLE IF NOT EXISTS traces (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    trace_id           VARCHAR(36)   NOT NULL,
    agent_id           VARCHAR(255)  NOT NULL,
    session_id         VARCHAR(255)  NOT NULL,
    trace_type         VARCHAR(50)   NOT NULL,
    latency_ms         BIGINT        NOT NULL,
    token_count        INTEGER,
    estimated_cost_usd NUMERIC(12,8),
    event_timestamp    TIMESTAMPTZ   NOT NULL,
    ingested_at        TIMESTAMPTZ   NOT NULL,

    CONSTRAINT pk_traces PRIMARY KEY (id),
    CONSTRAINT uq_traces_trace_id UNIQUE (trace_id),
    CONSTRAINT chk_traces_latency_ms CHECK (latency_ms >= 0),
    CONSTRAINT chk_traces_estimated_cost_usd CHECK (estimated_cost_usd IS NULL OR estimated_cost_usd >= 0),
    CONSTRAINT chk_traces_trace_type CHECK (
        trace_type IN ('LLM_CALL', 'TOOL_INVOCATION', 'DECISION_POINT', 'ERROR')
    )
);

-- Primary query pattern: all traces for an agent, most recent first
-- This is the most common query on the Trace Explorer page
CREATE INDEX idx_traces_agent_id_ingested_at ON traces (agent_id, ingested_at DESC);

-- Session drill-down: all traces in a session, ordered by event time
CREATE INDEX idx_traces_session_id_event_ts ON traces (session_id, event_timestamp ASC);

-- Error rate queries: count errors by agent in a time window
CREATE INDEX idx_traces_trace_type_agent_id ON traces (trace_type, agent_id) WHERE trace_type = 'ERROR';

-- Time-range scan for the metrics service (rare, but needs to be fast when used)
CREATE INDEX idx_traces_event_timestamp ON traces (event_timestamp DESC);

COMMENT ON TABLE traces IS 'Raw trace events from AI agents — the system of record for all observability data';
COMMENT ON COLUMN traces.trace_id IS 'Server-generated UUID assigned at ingestion time';
COMMENT ON COLUMN traces.trace_type IS 'LLM_CALL | TOOL_INVOCATION | DECISION_POINT | ERROR';
COMMENT ON COLUMN traces.latency_ms IS 'Duration of the operation as reported by the agent in milliseconds';
COMMENT ON COLUMN traces.event_timestamp IS 'Timestamp provided by the agent, or server time if omitted';
COMMENT ON COLUMN traces.ingested_at IS 'Server-side timestamp when this event was received and persisted';
