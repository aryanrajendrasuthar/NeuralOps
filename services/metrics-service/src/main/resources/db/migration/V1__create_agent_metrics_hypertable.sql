-- V1: Create the agent_metrics hypertable in TimescaleDB
-- This is where computed metric snapshots are stored for time-series queries.
-- The hypertable is partitioned by 'time' — TimescaleDB automatically manages
-- chunk creation, compression, and deletion based on the retention policy.
--
-- Why a hypertable instead of a regular PostgreSQL table?
-- At 1000 events/second, this table grows at ~86 million rows/day.
-- A regular table with that volume requires a full table scan for any time-range query.
-- TimescaleDB partitions the data into chunks (e.g., 1 day per chunk), so a query
-- for "the last 24 hours" scans exactly 1-2 chunks rather than billions of rows.

CREATE TABLE IF NOT EXISTS agent_metrics (
    time              TIMESTAMPTZ   NOT NULL,
    agent_id          VARCHAR(255)  NOT NULL,
    trace_type        VARCHAR(50)   NOT NULL,
    latency_ms        BIGINT        NOT NULL,
    token_count       INTEGER,
    cost_usd          NUMERIC(12,8),
    is_error          BOOLEAN       NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_agent_metrics_latency CHECK (latency_ms >= 0)
);

-- Convert to a TimescaleDB hypertable, partitioned by the 'time' column
-- chunk_time_interval: each chunk covers 1 hour of data
SELECT create_hypertable(
    'agent_metrics',
    'time',
    chunk_time_interval => INTERVAL '1 hour',
    if_not_exists => TRUE
);

-- Index on agent_id within the hypertable for per-agent queries
-- TimescaleDB automatically adds a time-based index; we add agent_id to support
-- filtered time-range queries without scanning all partitions
CREATE INDEX IF NOT EXISTS idx_agent_metrics_agent_id_time
    ON agent_metrics (agent_id, time DESC);

-- Continuous aggregate: pre-compute hourly statistics per agent
-- This allows the metrics service to answer "what was p95 latency over the last 7 days?"
-- in milliseconds rather than scanning millions of raw rows
CREATE MATERIALIZED VIEW IF NOT EXISTS agent_metrics_hourly
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time)  AS bucket,
    agent_id,
    trace_type,
    COUNT(*)                      AS event_count,
    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY latency_ms) AS p50_latency_ms,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms) AS p95_latency_ms,
    PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY latency_ms) AS p99_latency_ms,
    SUM(CASE WHEN is_error THEN 1 ELSE 0 END)                AS error_count,
    SUM(COALESCE(token_count, 0))                            AS total_tokens,
    SUM(COALESCE(cost_usd, 0))                               AS total_cost_usd
FROM agent_metrics
GROUP BY bucket, agent_id, trace_type
WITH NO DATA;

-- Refresh policy: update the continuous aggregate every 5 minutes
-- covering data from the last 2 hours (handles late-arriving events)
SELECT add_continuous_aggregate_policy(
    'agent_metrics_hourly',
    start_offset   => INTERVAL '2 hours',
    end_offset     => INTERVAL '5 minutes',
    schedule_interval => INTERVAL '5 minutes',
    if_not_exists  => TRUE
);

COMMENT ON TABLE agent_metrics IS 'TimescaleDB hypertable storing one row per trace event, partitioned by time';
COMMENT ON MATERIALIZED VIEW agent_metrics_hourly IS
    'Continuous aggregate: pre-computed hourly latency percentiles and cost totals per agent';
