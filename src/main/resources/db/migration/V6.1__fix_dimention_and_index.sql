
DROP INDEX IF EXISTS idx_user_memory_embedding;
ALTER TABLE user_memory ALTER COLUMN embedding TYPE vector(2048);

