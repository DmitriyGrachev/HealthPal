-- V7.2__fix_embedding_dimensions_nemotron_pa1.sql
DROP INDEX IF EXISTS idx_user_memory_embedding;

ALTER TABLE user_memory
ALTER COLUMN embedding TYPE vector(2048);

-- Для размерности 2048 HNSW не поддерживается, используйте IVFFlat если нужен индекс:
-- CREATE INDEX idx_user_memory_embedding_ivfflat ON user_memory USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);