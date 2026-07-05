-- pgvector HNSW indexes on vector columns support at most 2000 dimensions.
-- user_memory intentionally uses vector(2048), so keep exact vector search instead
-- of creating an invalid ANN index during fresh Flyway migrations.
DROP INDEX IF EXISTS idx_user_memory_embedding;
