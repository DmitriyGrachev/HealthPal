-- V19__add_outbox_next_retry_at.sql
-- Add next_retry_at column to telegram_delivery_outbox for exponential backoff

ALTER TABLE telegram_delivery_outbox
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP WITH TIME ZONE NULL;
