-- Per-user favorites pinned to a specific file version
CREATE SEQUENCE IF NOT EXISTS file_version_favorites_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE file_version_favorites
(
    id              BIGINT                      NOT NULL,
    user_id         UUID                        NOT NULL,
    file_version_id BIGINT                      NOT NULL,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_file_version_favorites PRIMARY KEY (id)
);

ALTER TABLE file_version_favorites
    ADD CONSTRAINT fk_fvf_on_file_version
        FOREIGN KEY (file_version_id) REFERENCES file_versions (id) ON DELETE CASCADE;

-- A user can favorite a given version at most once
ALTER TABLE file_version_favorites
    ADD CONSTRAINT uq_fvf_user_version UNIQUE (user_id, file_version_id);

CREATE INDEX idx_fvf_user_id ON file_version_favorites (user_id);
