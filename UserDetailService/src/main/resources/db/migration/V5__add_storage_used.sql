ALTER TABLE users
    ADD storage_used BIGINT;

ALTER TABLE users
    ALTER COLUMN storage_used SET DEFAULT 0;

UPDATE users
    SET storage_used = 0
    WHERE storage_used IS NULL;

ALTER TABLE users
    ALTER COLUMN storage_used SET NOT NULL;