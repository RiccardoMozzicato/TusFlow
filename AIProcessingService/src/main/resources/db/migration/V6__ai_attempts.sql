ALTER TABLE jobs
    ADD attempts INTEGER;

ALTER TABLE jobs
    ALTER COLUMN attempts SET NOT NULL;

DROP TABLE vector_store CASCADE;