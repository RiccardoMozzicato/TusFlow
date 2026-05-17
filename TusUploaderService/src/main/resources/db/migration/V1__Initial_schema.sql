-- Initial database schema
-- Created from existing DDL

CREATE TABLE uploads
(
    uuid         UUID                        NOT NULL,
    filename     VARCHAR(255),
    content_type VARCHAR(255),
    size         BIGINT,
    total_offset BIGINT,
    metadata     JSONB,
    checksum     VARCHAR(255),
    status       VARCHAR(255)                NOT NULL,
    created_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    expires_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    object_key   VARCHAR(255),
    merge_mode   VARCHAR(255)                NOT NULL,
    chunk_count  INTEGER                     NOT NULL,
    CONSTRAINT pk_uploads PRIMARY KEY (uuid)
);

CREATE TABLE files
(
    uuid            UUID                        NOT NULL,
    status          VARCHAR(255)                NOT NULL,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_update_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_filename   VARCHAR(255)                NOT NULL,
    current_version BIGINT                      NOT NULL,
    CONSTRAINT pk_files PRIMARY KEY (uuid)
);

CREATE TABLE file_versions
(
    id             BIGINT                      NOT NULL,
    version        BIGINT                      NOT NULL,
    file_uuid      UUID,
    filename       VARCHAR(255)                NOT NULL,
    size           BIGINT,
    metadata       JSONB,
    checksum       VARCHAR(255),
    content_type   VARCHAR(255)                NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_update_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    object_key     VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_file_versions PRIMARY KEY (id)
);

ALTER TABLE file_versions
    ADD CONSTRAINT FK_FILE_VERSIONS_ON_FILE_UUID FOREIGN KEY (file_uuid) REFERENCES files (uuid);

CREATE TABLE file_shares
(
    id         BIGINT                      NOT NULL,
    userid     UUID                        NOT NULL,
    permission VARCHAR(255)                NOT NULL,
    timestamp  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    file_uuid  UUID,
    CONSTRAINT pk_file_shares PRIMARY KEY (id)
);

ALTER TABLE file_shares
    ADD CONSTRAINT FK_FILE_SHARES_ON_FILE_UUID FOREIGN KEY (file_uuid) REFERENCES files (uuid);

CREATE TABLE files_outbox_events (
    id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_files_outbox_events PRIMARY KEY (id)
    );

CREATE INDEX idx_outbox_created_at ON files_outbox_events(created_at);