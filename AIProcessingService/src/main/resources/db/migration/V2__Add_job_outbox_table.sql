-- Add job_outbox table for Transactional Outbox Pattern

CREATE TABLE job_outbox
(
    id         UUID      NOT NULL DEFAULT gen_random_uuid(),
    event_type VARCHAR(255) NOT NULL,
    payload    JSONB     NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT job_outbox_pk PRIMARY KEY (id)
);

-- Index for efficient Debezium polling
CREATE INDEX idx_job_outbox_created_at ON job_outbox (created_at);

COMMENT ON TABLE job_outbox IS 'Outbox table for publishing job events to Kafka via Debezium';
COMMENT ON COLUMN job_outbox.id IS 'Unique event identifier';
COMMENT ON COLUMN job_outbox.payload IS 'Enhanced metadata (JSONB format)';
COMMENT ON COLUMN job_outbox.created_at IS 'Timestamp when the event was created';

