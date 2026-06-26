-- Tracks when a job last changed status, so the dashboard can count
-- jobs completed since a point in time (e.g. "completed today").
ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT now();

COMMENT ON COLUMN jobs.updated_at IS 'Timestamp of the last job update (status transition)';

-- The stats endpoint aggregates by status (and updated_at for COMPLETED).
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs (status);
