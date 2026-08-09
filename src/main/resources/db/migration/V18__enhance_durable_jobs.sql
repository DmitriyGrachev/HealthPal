-- V18__enhance_durable_jobs.sql
-- Add idempotency key to durable_jobs table for exact-once creation and execution deduplication

ALTER TABLE durable_jobs ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_durable_jobs_idempotency_key 
ON durable_jobs(idempotency_key) 
WHERE idempotency_key IS NOT NULL;
