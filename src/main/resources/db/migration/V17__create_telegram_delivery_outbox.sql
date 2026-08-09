-- V17__create_telegram_delivery_outbox.sql
-- Outbox ledger for durable Telegram message delivery, retries, and fallback

CREATE TABLE IF NOT EXISTS telegram_delivery_outbox (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    text TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    error_message TEXT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX IF NOT EXISTS idx_telegram_delivery_outbox_status ON telegram_delivery_outbox(status);
