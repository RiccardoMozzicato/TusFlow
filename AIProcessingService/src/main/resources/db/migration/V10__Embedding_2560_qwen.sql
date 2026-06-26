-- Switch the embedding model from Ollama nomic-embed-text (768) to Qwen3-Embedding-4B
-- (2560), served either by local Ollama (qwen3-embedding:4b) or by OpenWebUI. Both emit
-- 2560-dim vectors in the same space.
--
-- DESTRUCTIVE: existing vectors are 768-dim nomic embeddings — a different model AND a
-- different dimensionality — so they cannot live in a 2560 column and would be invalid
-- for search anyway. We clear them; documents must be re-embedded (re-upload or re-run).

-- 1. Clear incompatible embeddings and their tracking rows (child first; the FK is
--    ON DELETE CASCADE, but some tracking rows may have a null vector_store_id).
DELETE FROM file_version_embeddings;
DELETE FROM vector_store;

-- 2. The HNSW index is bound to the column's 768 dimensionality; drop it before retyping.
--    NOTE: pgvector HNSW/IVFFlat indexes support at most 2000 dimensions for the `vector`
--    type, and 2560 exceeds that — so we deliberately do NOT recreate an ANN index.
--    Similarity search falls back to an exact sequential scan. If ANN speed is needed at
--    this dimensionality later, switch the column to halfvec(2560) + halfvec_cosine_ops
--    (supports up to 4000 dims) or truncate the model output to <= 2000 dims.
DROP INDEX IF EXISTS idx_vector_store_embedding_hnsw;

-- 3. Widen the embedding column to the new model's native dimensionality.
ALTER TABLE vector_store
    ALTER COLUMN embedding TYPE vector(2560);

COMMENT ON COLUMN vector_store.embedding IS
    '2560-dimensional vector embedding (Qwen3-Embedding-4B); no ANN index (exceeds pgvector 2000-dim HNSW limit) — exact sequential-scan search';
