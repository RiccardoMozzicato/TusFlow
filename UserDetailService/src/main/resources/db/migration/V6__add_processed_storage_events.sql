CREATE TABLE processed_storage_events
(
    event_id     UUID                        NOT NULL,
    processed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_storage_events PRIMARY KEY (event_id)
);
