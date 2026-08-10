-- Bind durable personal work to its current user or Telegram link.

CREATE FUNCTION fitnessapp_event_user_id(payload TEXT)
RETURNS BIGINT
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $$
DECLARE
    raw_user_id TEXT;
BEGIN
    raw_user_id := payload::jsonb ->> 'userId';
    IF raw_user_id IS NULL OR raw_user_id !~ '^[1-9][0-9]*$' THEN
        RETURN NULL;
    END IF;
    RETURN raw_user_id::BIGINT;
EXCEPTION
    WHEN invalid_text_representation OR numeric_value_out_of_range THEN
        RETURN NULL;
END;
$$;

ALTER TABLE event_publication
    ADD COLUMN user_id BIGINT
        GENERATED ALWAYS AS (fitnessapp_event_user_id(serialized_event)) STORED;

DELETE FROM event_publication publication
 WHERE publication.user_id IS NOT NULL
   AND NOT EXISTS (
       SELECT 1
         FROM users app_user
        WHERE app_user.id = publication.user_id
   );

CREATE INDEX idx_event_publication_user_id
    ON event_publication(user_id)
    WHERE user_id IS NOT NULL;

ALTER TABLE event_publication
    ADD CONSTRAINT fk_event_publication_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE telegram_delivery_outbox
    ADD COLUMN user_id BIGINT;

ALTER TABLE conversation_state
    ADD COLUMN user_id BIGINT;

ALTER TABLE conversation_history
    ADD COLUMN user_id BIGINT;

-- Multiple links for one chat make legacy ownership unknowable.
DELETE FROM conversation_state state
 WHERE (SELECT COUNT(*) FROM telegram_users link WHERE link.chat_id = state.chat_id) <> 1;

DELETE FROM conversation_history history
 WHERE (SELECT COUNT(*) FROM telegram_users link WHERE link.chat_id = history.chat_id) <> 1;

DELETE FROM telegram_delivery_outbox outbox
 WHERE (SELECT COUNT(*) FROM telegram_users link WHERE link.chat_id = outbox.chat_id) > 1
    OR (outbox.status IN ('PENDING', 'SENDING')
        AND NOT EXISTS (SELECT 1 FROM telegram_users link WHERE link.chat_id = outbox.chat_id));

-- Telegram private chats are single-owner. Retain the newest deterministic link.
WITH ranked_links AS (
    SELECT telegram_id,
           ROW_NUMBER() OVER (
               PARTITION BY chat_id
               ORDER BY linked_at DESC NULLS LAST, telegram_id DESC
           ) AS owner_rank
      FROM telegram_users
)
DELETE FROM telegram_users link
 USING ranked_links ranked
 WHERE link.telegram_id = ranked.telegram_id
   AND ranked.owner_rank > 1;

UPDATE conversation_state state
   SET user_id = (
       SELECT link.user_id
         FROM telegram_users link
        WHERE link.chat_id = state.chat_id
   );

UPDATE conversation_history history
   SET user_id = (
       SELECT link.user_id
         FROM telegram_users link
        WHERE link.chat_id = history.chat_id
   );

UPDATE telegram_delivery_outbox outbox
   SET user_id = (
       SELECT link.user_id
         FROM telegram_users link
        WHERE link.chat_id = outbox.chat_id
   )
 WHERE (SELECT COUNT(*) FROM telegram_users link WHERE link.chat_id = outbox.chat_id) = 1;

ALTER TABLE telegram_users
    ADD CONSTRAINT uq_telegram_user_user_chat UNIQUE (user_id, chat_id),
    ADD CONSTRAINT uq_telegram_user_chat UNIQUE (chat_id);

ALTER TABLE conversation_state
    ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT fk_conversation_state_telegram_link
        FOREIGN KEY (user_id, chat_id)
        REFERENCES telegram_users(user_id, chat_id)
        ON DELETE CASCADE;

ALTER TABLE conversation_history
    ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT fk_conversation_history_telegram_link
        FOREIGN KEY (user_id, chat_id)
        REFERENCES telegram_users(user_id, chat_id)
        ON DELETE CASCADE;

ALTER TABLE telegram_delivery_outbox
    ADD CONSTRAINT fk_telegram_outbox_telegram_link
        FOREIGN KEY (user_id, chat_id)
        REFERENCES telegram_users(user_id, chat_id)
        ON DELETE CASCADE;

CREATE INDEX idx_telegram_outbox_user_id
    ON telegram_delivery_outbox(user_id)
    WHERE user_id IS NOT NULL;
