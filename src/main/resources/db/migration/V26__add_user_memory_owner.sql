-- User Memory is derived data. Remove rows that cannot be assigned to a valid User
-- before adding constraint-backed ownership for all future VectorStore writes.
DELETE FROM user_memory
 WHERE metadata IS NULL
    OR NOT (metadata ? 'user_id')
    OR NOT ((metadata ->> 'user_id') ~ '^[1-9][0-9]*$');

DELETE FROM user_memory memory
 WHERE NOT EXISTS (
     SELECT 1
       FROM users app_user
      WHERE app_user.id = (memory.metadata ->> 'user_id')::bigint
 );

ALTER TABLE user_memory
    ADD COLUMN user_id BIGINT
    GENERATED ALWAYS AS ((metadata ->> 'user_id')::bigint) STORED NOT NULL;

CREATE INDEX idx_user_memory_user_id ON user_memory(user_id);

ALTER TABLE user_memory
    ADD CONSTRAINT fk_user_memory_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
