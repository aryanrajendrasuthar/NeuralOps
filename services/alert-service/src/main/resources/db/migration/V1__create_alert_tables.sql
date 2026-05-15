-- Alert rules define the conditions that trigger an alert for a specific agent
-- (or all agents when agent_id is NULL).
--
-- metric values: LATENCY_P99, LATENCY_P95, ERROR_RATE, ANOMALY_SCORE
-- operator values: GT (greater than), LT (less than)
CREATE TABLE IF NOT EXISTS alert_rules (
    id           BIGSERIAL    PRIMARY KEY,
    agent_id     VARCHAR(255),
    metric       VARCHAR(50)  NOT NULL,
    operator     VARCHAR(10)  NOT NULL,
    threshold    DOUBLE PRECISION NOT NULL,
    webhook_url  TEXT         NOT NULL,
    is_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_metric CHECK (metric IN ('LATENCY_P99','LATENCY_P95','ERROR_RATE','ANOMALY_SCORE')),
    CONSTRAINT chk_operator CHECK (operator IN ('GT','LT'))
);

CREATE INDEX IF NOT EXISTS idx_alert_rules_agent_id
    ON alert_rules (agent_id)
    WHERE agent_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_alert_rules_enabled
    ON alert_rules (is_enabled)
    WHERE is_enabled = TRUE;

-- Alert events record every time a rule fired, and track webhook delivery status.
--
-- webhook_status values: PENDING, DELIVERED, FAILED
CREATE TABLE IF NOT EXISTS alert_events (
    id               BIGSERIAL    PRIMARY KEY,
    rule_id          BIGINT       NOT NULL REFERENCES alert_rules(id),
    agent_id         VARCHAR(255) NOT NULL,
    triggered_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    metric_value     DOUBLE PRECISION NOT NULL,
    webhook_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    webhook_attempts INTEGER      NOT NULL DEFAULT 0,
    last_attempt_at  TIMESTAMPTZ,

    CONSTRAINT chk_webhook_status CHECK (webhook_status IN ('PENDING','DELIVERED','FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_alert_events_rule_id
    ON alert_events (rule_id);

CREATE INDEX IF NOT EXISTS idx_alert_events_agent_id_triggered
    ON alert_events (agent_id, triggered_at DESC);

CREATE INDEX IF NOT EXISTS idx_alert_events_status
    ON alert_events (webhook_status)
    WHERE webhook_status IN ('PENDING','FAILED');
