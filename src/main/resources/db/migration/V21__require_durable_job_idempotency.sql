UPDATE durable_jobs
SET idempotency_key = 'legacy:' || id
WHERE idempotency_key IS NULL;

ALTER TABLE durable_jobs
    ALTER COLUMN idempotency_key SET NOT NULL;
