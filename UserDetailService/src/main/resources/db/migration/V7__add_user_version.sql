ALTER TABLE users
    ADD version BIGINT;

UPDATE users
    SET version = 0
    WHERE version IS NULL;

ALTER TABLE users
    ALTER COLUMN version SET DEFAULT 0;

ALTER TABLE users
    ALTER COLUMN version SET NOT NULL;
