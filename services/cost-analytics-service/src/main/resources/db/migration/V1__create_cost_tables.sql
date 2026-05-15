-- Stores daily cost rollups per agent. Each row represents one agent's aggregated
-- cost for a single UTC day, accumulated from trace events consumed from Kafka.
CREATE TABLE IF NOT EXISTS agent_daily_cost (
    id             BIGSERIAL PRIMARY KEY,
    agent_id       VARCHAR(255) NOT NULL,
    cost_date      DATE         NOT NULL,
    total_cost_usd NUMERIC(14, 8) NOT NULL DEFAULT 0,
    trace_count    BIGINT       NOT NULL DEFAULT 0,
    token_count    BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_agent_daily_cost UNIQUE (agent_id, cost_date),
    CONSTRAINT chk_cost_non_negative CHECK (total_cost_usd >= 0),
    CONSTRAINT chk_trace_count_non_negative CHECK (trace_count >= 0),
    CONSTRAINT chk_token_count_non_negative CHECK (token_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_agent_daily_cost_agent_date
    ON agent_daily_cost (agent_id, cost_date DESC);

-- Stores linear regression forecast results. A row is inserted each time the
-- forecast job runs for a given agent, preserving forecast history.
CREATE TABLE IF NOT EXISTS agent_cost_forecast (
    id             BIGSERIAL PRIMARY KEY,
    agent_id       VARCHAR(255)    NOT NULL,
    forecast_date  DATE            NOT NULL,
    forecast_day   INTEGER         NOT NULL,
    predicted_cost NUMERIC(14, 8)  NOT NULL,
    r_squared      DOUBLE PRECISION,
    generated_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_agent_cost_forecast UNIQUE (agent_id, forecast_date, forecast_day),
    CONSTRAINT chk_forecast_day_positive CHECK (forecast_day >= 1),
    CONSTRAINT chk_predicted_cost_non_negative CHECK (predicted_cost >= 0)
);

CREATE INDEX IF NOT EXISTS idx_agent_cost_forecast_agent_date
    ON agent_cost_forecast (agent_id, forecast_date DESC);
