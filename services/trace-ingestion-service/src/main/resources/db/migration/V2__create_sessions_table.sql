-- V2: Create sessions table
-- One session represents a single execution of an agent for a given task or conversation.
-- Multiple trace events share the same session_id.

CREATE TABLE IF NOT EXISTS sessions (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    session_id   VARCHAR(255)  NOT NULL,
    agent_id     VARCHAR(255)  NOT NULL,
    started_at   TIMESTAMPTZ   NOT NULL,
    last_event_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_sessions PRIMARY KEY (id),
    CONSTRAINT uq_sessions_session_id UNIQUE (session_id)
);

CREATE INDEX idx_sessions_agent_id ON sessions (agent_id);
CREATE INDEX idx_sessions_started_at ON sessions (started_at DESC);

COMMENT ON TABLE sessions IS 'Agent execution sessions — each session groups a sequence of trace events';
COMMENT ON COLUMN sessions.session_id IS 'External session identifier provided by the agent';
COMMENT ON COLUMN sessions.last_event_at IS 'Timestamp of the most recent trace event in this session';
