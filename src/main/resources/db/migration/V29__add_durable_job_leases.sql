-- Durable-job lease fencing. V1-V28 are immutable; all cleanup is forward-only.

ALTER TABLE durable_jobs
    ADD COLUMN IF NOT EXISTS lease_generation BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(128),
    ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;

-- A RUNNING row from before lease fencing is not safe to resume in place.
-- Retryable rows return to the due queue; final-attempt rows become terminal.
UPDATE durable_jobs
   SET status = 'PENDING',
       next_retry_at = NOW(),
       error_message = 'CLAIM_TIMEOUT_RETRYABLE',
       lease_owner = NULL,
       lease_expires_at = NULL,
       claimed_at = NULL,
       updated_at = NOW()
 WHERE status = 'RUNNING'
   AND (
       lease_owner IS NULL
       OR length(trim(lease_owner)) = 0
       OR lease_expires_at IS NULL
       OR claimed_at IS NULL
       OR lease_expires_at <= NOW()
   )
   AND attempts < max_attempts;

UPDATE durable_jobs
   SET status = 'FAILED',
       next_retry_at = NULL,
       error_message = 'CLAIM_TIMEOUT_MAX_ATTEMPTS',
       lease_owner = NULL,
       lease_expires_at = NULL,
       claimed_at = NULL,
       updated_at = NOW()
 WHERE status = 'RUNNING'
   AND (
       lease_owner IS NULL
       OR length(trim(lease_owner)) = 0
       OR lease_expires_at IS NULL
       OR claimed_at IS NULL
       OR lease_expires_at <= NOW()
   )
   AND attempts >= max_attempts;

UPDATE durable_jobs
   SET status = 'FAILED',
       next_retry_at = NULL,
       error_message = 'CLAIM_TIMEOUT_MAX_ATTEMPTS',
       lease_owner = NULL,
       lease_expires_at = NULL,
       claimed_at = NULL,
       updated_at = NOW()
 WHERE status = 'PENDING'
   AND attempts >= max_attempts;

-- Legacy job failures may contain provider text, payloads, or other private data.
-- Preserve only the stable allowlisted values; scrub every other non-null value.
UPDATE durable_jobs
   SET error_message = CASE
       WHEN error_message IS NULL THEN NULL
       WHEN error_message ~ '^(UNKNOWN_FAILURE|PROVIDER_UNAVAILABLE|INVALID_PAYLOAD|INTERNAL_FAILURE|CLAIM_TIMEOUT_MAX_ATTEMPTS|CLAIM_TIMEOUT_RETRYABLE|NO_EXECUTOR)(:(external-service|payload|application))?$'
           THEN error_message
       ELSE 'UNKNOWN_FAILURE'
   END
 WHERE error_message IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_durable_jobs_pending_due
    ON durable_jobs (next_retry_at, id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_durable_jobs_running_expiry
    ON durable_jobs (lease_expires_at, id)
    WHERE status = 'RUNNING';

CREATE INDEX IF NOT EXISTS idx_durable_jobs_owner_generation
    ON durable_jobs (lease_owner, lease_generation)
    WHERE status = 'RUNNING';

ALTER TABLE durable_jobs
    ADD CONSTRAINT chk_durable_job_lease_generation_nonnegative
        CHECK (lease_generation >= 0) NOT VALID,
    ADD CONSTRAINT chk_durable_job_running_complete_lease
        CHECK (
            status <> 'RUNNING'
            OR (
                lease_owner IS NOT NULL
                AND length(trim(lease_owner)) > 0
                AND lease_expires_at IS NOT NULL
                AND claimed_at IS NOT NULL
            )
        ) NOT VALID,
    ADD CONSTRAINT chk_durable_job_non_running_without_lease
        CHECK (
            status = 'RUNNING'
            OR (lease_owner IS NULL AND lease_expires_at IS NULL AND claimed_at IS NULL)
        ) NOT VALID,
    ADD CONSTRAINT chk_durable_job_pending_not_exhausted
        CHECK (status <> 'PENDING' OR attempts < max_attempts) NOT VALID;

ALTER TABLE durable_jobs
    ADD CONSTRAINT chk_durable_job_error_message_safe
        CHECK (
            error_message IS NULL
            OR error_message ~ '^(UNKNOWN_FAILURE|PROVIDER_UNAVAILABLE|INVALID_PAYLOAD|INTERNAL_FAILURE|CLAIM_TIMEOUT_MAX_ATTEMPTS|CLAIM_TIMEOUT_RETRYABLE|NO_EXECUTOR)(:(external-service|payload|application))?$'
        ) NOT VALID;

ALTER TABLE durable_jobs
    VALIDATE CONSTRAINT chk_durable_job_lease_generation_nonnegative;
ALTER TABLE durable_jobs
    VALIDATE CONSTRAINT chk_durable_job_running_complete_lease;
ALTER TABLE durable_jobs
    VALIDATE CONSTRAINT chk_durable_job_non_running_without_lease;
ALTER TABLE durable_jobs
    VALIDATE CONSTRAINT chk_durable_job_pending_not_exhausted;
ALTER TABLE durable_jobs
    VALIDATE CONSTRAINT chk_durable_job_error_message_safe;
