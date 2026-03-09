-- Add foreign key column in uploads table
ALTER TABLE uploads
    ADD COLUMN file_version_id BIGINT;

-- Add foreign key constraint
ALTER TABLE uploads
    ADD CONSTRAINT fk_upload_file_version
        FOREIGN KEY (file_version_id)
            REFERENCES file_versions(id)
            ON DELETE SET NULL ;