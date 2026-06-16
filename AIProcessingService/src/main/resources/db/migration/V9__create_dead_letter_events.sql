-- Audit table for Kafka records that exhausted their retries and were dead-lettered.

CREATE TABLE dead_letter_events
(
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    original_topic    VARCHAR(255) NOT NULL,
    payload           TEXT         NOT NULL,
    exception_type    VARCHAR(512),
    exception_message TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT dead_letter_events_pk PRIMARY KEY (id)
);

CREATE INDEX idx_dead_letter_events_created_at ON dead_letter_events (created_at);
CREATE INDEX idx_dead_letter_events_original_topic ON dead_letter_events (original_topic);

COMMENT ON TABLE dead_letter_events IS 'Audit log of Kafka records dead-lettered after retries were exhausted';
COMMENT ON COLUMN dead_letter_events.original_topic IS 'The source topic whose processing ultimately failed';
COMMENT ON COLUMN dead_letter_events.payload IS 'Verbatim message body from the dead-letter record';
COMMENT ON COLUMN dead_letter_events.exception_message IS 'Last exception message reported by Spring Kafka';
