-- Create file_version_embeddings tracking table

CREATE TABLE IF NOT EXISTS file_version_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_version_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    vector_store_id UUID,
    content_preview TEXT,
    chunk_tokens INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_file_version_chunk UNIQUE(file_version_id, chunk_index),
    CONSTRAINT fk_vector_store FOREIGN KEY (vector_store_id)
        REFERENCES vector_store(id) ON DELETE CASCADE
);

-- Index for querying embeddings by file_version_id
CREATE INDEX IF NOT EXISTS idx_file_version_embeddings_fk
ON file_version_embeddings(file_version_id);

-- Index for ordering by chunk_index
CREATE INDEX IF NOT EXISTS idx_file_version_embeddings_chunk
ON file_version_embeddings(file_version_id, chunk_index);

COMMENT ON TABLE file_version_embeddings IS 'Tracking table for embeddings associated with specific file versions';
COMMENT ON COLUMN file_version_embeddings.id IS 'Unique identifier for the tracking record';
COMMENT ON COLUMN file_version_embeddings.file_version_id IS 'Numeric ID of the file version (BIGINT from TusUploaderService)';
COMMENT ON COLUMN file_version_embeddings.chunk_index IS 'Sequential index of the chunk within the file (0-based)';
COMMENT ON COLUMN file_version_embeddings.vector_store_id IS 'Foreign key to the vector_store table';
COMMENT ON COLUMN file_version_embeddings.content_preview IS 'First 200 characters of the chunk for preview';
COMMENT ON COLUMN file_version_embeddings.chunk_tokens IS 'Approximate number of tokens in the chunk';
COMMENT ON COLUMN file_version_embeddings.created_at IS 'Timestamp when the embedding was created';

