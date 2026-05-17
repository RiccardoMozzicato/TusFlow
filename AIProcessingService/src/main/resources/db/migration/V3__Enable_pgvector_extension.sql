-- Enable pgvector extension for vector embeddings

CREATE EXTENSION IF NOT EXISTS vector;

COMMENT ON EXTENSION vector IS 'pgvector extension for storing and querying vector embeddings';

