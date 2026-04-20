-- V7__fix_embedding_dimensions.sql
-- Change dimension from 768 to 1536 for OpenAI text-embedding-3-small

-- 1. Drop existing HNSW index (dimension specific)
DROP INDEX IF EXISTS idx_user_memory_embedding;

-- 2. Alter column type
ALTER TABLE user_memory 
ALTER COLUMN embedding TYPE vector(1536);

-- 3. Recreate HNSW index
CREATE INDEX idx_user_memory_embedding 
ON user_memory USING hnsw (embedding vector_cosine_ops);
