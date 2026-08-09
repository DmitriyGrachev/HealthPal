ALTER TABLE telegram_delivery_outbox
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP WITH TIME ZONE NULL;

CREATE INDEX IF NOT EXISTS idx_telegram_outbox_retry_claim
    ON telegram_delivery_outbox(status, next_retry_at, id);
