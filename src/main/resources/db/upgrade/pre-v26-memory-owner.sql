-- Run immediately before V26 while writers are stopped.
LOCK TABLE user_memory IN SHARE ROW EXCLUSIVE MODE;

-- Remove unsafe metadata before the first bigint cast. The length and lexical
-- checks are range-safe for the full signed BIGINT domain.
DELETE FROM user_memory memory
 WHERE memory.metadata IS NULL
    OR NOT (memory.metadata ? 'user_id')
    OR jsonb_typeof(memory.metadata -> 'user_id') IS NULL
    OR jsonb_typeof(memory.metadata -> 'user_id') NOT IN ('string', 'number')
    OR (memory.metadata ->> 'user_id') !~ '^[1-9][0-9]*$'
    OR length(memory.metadata ->> 'user_id') > 19
    OR (
        length(memory.metadata ->> 'user_id') = 19
        AND (memory.metadata ->> 'user_id') > '9223372036854775807'
    );

DELETE FROM user_memory memory
 WHERE NOT EXISTS (
           SELECT 1
             FROM users app_user
            WHERE app_user.id = (memory.metadata ->> 'user_id')::bigint
       );
