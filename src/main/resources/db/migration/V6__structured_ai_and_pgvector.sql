-- Phase 0: Semantic Infrastructure and Structured AI
CREATE EXTENSION IF NOT EXISTS vector;

-- Upgrade AI Insights for backward compatibility and structured data
ALTER TABLE ai_insights 
    ADD COLUMN IF NOT EXISTS structured_response JSONB,
    ADD COLUMN IF NOT EXISTS schema_version INT NOT NULL DEFAULT 1;

-- The Memory Vault aligned with Spring AI PgVectorStore expectations
CREATE TABLE IF NOT EXISTS user_memory (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content     TEXT NOT NULL,
    metadata    JSONB, 
    embedding   vector(768) 
);

-- Index for HNSW (Semantic Search)
CREATE INDEX IF NOT EXISTS idx_user_memory_embedding 
    ON user_memory USING hnsw (embedding vector_cosine_ops);

-- Index for JSONB Metadata (Filtering performance for user_id, etc.)
CREATE INDEX IF NOT EXISTS idx_user_memory_metadata
    ON user_memory USING gin (metadata);
