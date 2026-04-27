-- V6__structured_ai_and_pgvector.sql (исправленная)

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE ai_insights
    ADD COLUMN IF NOT EXISTS structured_response JSONB,
    ADD COLUMN IF NOT EXISTS schema_version INT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS user_memory (
                                           id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content     TEXT NOT NULL,
    metadata    JSONB,
    embedding   vector(1536)   -- 1536 – для OpenAI text-embedding-3-small
    );

CREATE INDEX IF NOT EXISTS idx_user_memory_embedding
    ON user_memory USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_user_memory_metadata
    ON user_memory USING gin (metadata);