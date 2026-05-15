-- V1: Create agents table
-- Stores one record per unique AI agent that has submitted at least one trace event.
-- The agent_id column is the external identifier provided by the agent in each trace event.

CREATE TABLE IF NOT EXISTS agents (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    agent_id    VARCHAR(255) NOT NULL,
    name        VARCHAR(255),
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_agents PRIMARY KEY (id),
    CONSTRAINT uq_agents_agent_id UNIQUE (agent_id)
);

CREATE INDEX idx_agents_last_seen_at ON agents (last_seen_at DESC);

COMMENT ON TABLE agents IS 'Registry of AI agents that have submitted trace events to NeuralOps';
COMMENT ON COLUMN agents.agent_id IS 'External identifier provided by the agent in each trace event';
COMMENT ON COLUMN agents.last_seen_at IS 'Timestamp of the most recent trace event from this agent';
