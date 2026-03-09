-- Create vector_store table for Spring AI VectorStore

CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    metadata JSONB,
    embedding VECTOR(768) NOT NULL,  -- nomic-embed-text usa 768 dimensioni
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- HNSW index for fast similarity search (cosine distance)
-- HNSW (Hierarchical Navigable Small World) is better than IVFFlat for most use cases
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding_hnsw
ON vector_store
USING hnsw (embedding vector_cosine_ops);

-- GIN index on metadata JSONB for fast filtering
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata
ON vector_store
USING gin (metadata);

-- Functional indexes for common metadata queries
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_file_id
ON vector_store ((metadata->>'fileId'));

CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_file_version_id
ON vector_store ((metadata->>'fileVersionId'));

CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_user_id
ON vector_store ((metadata->>'ownerUserId'));

COMMENT ON TABLE vector_store IS 'Stores document embeddings for semantic search using Spring AI VectorStore';
COMMENT ON COLUMN vector_store.id IS 'Unique identifier for the embedding';
COMMENT ON COLUMN vector_store.content IS 'Text content of the document chunk';
COMMENT ON COLUMN vector_store.metadata IS 'JSONB metadata (fileId, fileVersionId, chunkIndex, etc.)';
COMMENT ON COLUMN vector_store.embedding IS '768-dimensional vector embedding from Ollama nomic-embed-text';
COMMENT ON COLUMN vector_store.created_at IS 'Timestamp when the embedding was created';

