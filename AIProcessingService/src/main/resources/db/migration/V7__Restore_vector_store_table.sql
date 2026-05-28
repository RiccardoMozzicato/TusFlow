-- Restore vector_store table dropped unintentionally in V6
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    metadata JSONB,
    embedding VECTOR(768) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding_hnsw
ON vector_store
USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_vector_store_metadata
ON vector_store
USING gin (metadata);

CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_file_id
ON vector_store ((metadata->>'fileId'));

CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_file_version_id
ON vector_store ((metadata->>'fileVersionId'));

CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_user_id
ON vector_store ((metadata->>'ownerUserId'));