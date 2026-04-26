ALTER TABLE vector_store ALTER COLUMN embedding TYPE vector(1536);

DROP INDEX IF EXISTS vector_store_embedding_idx;
CREATE INDEX vector_store_embedding_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);