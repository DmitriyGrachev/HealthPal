ALTER TABLE user_memory
ALTER COLUMN embedding TYPE vector(2000);

DROP INDEX IF EXISTS idx_user_memory_embedding;

CREATE INDEX idx_user_memory_embedding
    ON user_memory USING hnsw (embedding vector_cosine_ops);