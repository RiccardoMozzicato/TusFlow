-- Initial schema for AI Processing Service

CREATE TABLE jobs
(
    jobid    UUID         NOT NULL,
    fileid   UUID         NOT NULL,
    version  BIGINT       NOT NULL,
    job_type VARCHAR(255) NOT NULL,
    payload  JSONB,
    status   VARCHAR(255) NOT NULL,
    CONSTRAINT jobs_pk PRIMARY KEY (jobid)
);

COMMENT ON TABLE jobs IS 'Stores AI processing jobs';
COMMENT ON COLUMN jobs.jobid IS 'Unique job identifier';
COMMENT ON COLUMN jobs.fileid IS 'Reference to the file being processed';
COMMENT ON COLUMN jobs.version IS 'Version of the file';
COMMENT ON COLUMN jobs.job_type IS 'Type of AI processing job';
COMMENT ON COLUMN jobs.payload IS 'Additional job metadata';
COMMENT ON COLUMN jobs.status IS 'Current status of the job (PENDING, PROCESSING, COMPLETED, FAILED)';

