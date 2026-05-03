ALTER TABLE file_versions
    ADD COLUMN IF NOT EXISTS ai_metadata JSONB;

