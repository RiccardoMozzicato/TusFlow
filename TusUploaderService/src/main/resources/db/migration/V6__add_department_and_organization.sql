ALTER TABLE uploads
DROP
CONSTRAINT fk_upload_file_version;

ALTER TABLE files
    ADD department VARCHAR(255);

ALTER TABLE files
    ADD organization VARCHAR(255);

ALTER TABLE files
    ADD owner_id VARCHAR(255);

ALTER TABLE files
    ADD sharing_scope SMALLINT;

ALTER TABLE uploads
    ADD department VARCHAR(255);

ALTER TABLE uploads
    ADD organization VARCHAR(255);

ALTER TABLE uploads
    ADD owner_id VARCHAR(255);

ALTER TABLE uploads
    ADD sharing_scope SMALLINT;

ALTER TABLE uploads
    DROP COLUMN IF EXISTS completed_at;

ALTER TABLE files
    ALTER COLUMN organization SET NOT NULL;

ALTER TABLE uploads
    ALTER COLUMN organization SET NOT NULL;

ALTER TABLE files
    ALTER COLUMN owner_id SET NOT NULL;

ALTER TABLE uploads
    ALTER COLUMN owner_id SET NOT NULL;

ALTER TABLE files
    ALTER COLUMN sharing_scope SET NOT NULL;

ALTER TABLE uploads
    ALTER COLUMN sharing_scope SET NOT NULL;