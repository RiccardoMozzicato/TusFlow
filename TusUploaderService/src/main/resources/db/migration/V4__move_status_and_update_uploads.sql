ALTER TABLE file_versions
    ADD status VARCHAR(255);

ALTER TABLE file_versions
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE files
    DROP COLUMN status;

ALTER TABLE uploads
    ALTER COLUMN object_key DROP NOT NULL;