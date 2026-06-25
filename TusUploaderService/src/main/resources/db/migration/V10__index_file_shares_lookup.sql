-- Backs the "shared with me" EXISTS subquery in FileSpecifications.buildFilesByScopes.
-- The FK_FILE_SHARES_ON_FILE_UUID foreign key does not create an index in Postgres,
-- so the per-file/per-user share lookup would otherwise scan file_shares.
CREATE INDEX IF NOT EXISTS idx_file_shares_file_user
    ON file_shares (file_uuid, userid);
