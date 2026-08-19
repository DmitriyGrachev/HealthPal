-- Telegram delivery fencing. V1-V29 remain immutable.

ALTER TABLE telegram_delivery_outbox
    ADD COLUMN IF NOT EXISTS lease_generation BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(128),
    ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMPTZ;

-- The V25 status check cannot accept DELIVERY_UNKNOWN during normalization.
ALTER TABLE telegram_delivery_outbox
    DROP CONSTRAINT IF EXISTS chk_telegram_outbox_status;

-- A legacy SENDING row has no complete fence, so an owned row is retained as
-- an explicitly uncertain audit record and anonymous content is discarded.
UPDATE telegram_delivery_outbox
   SET status = 'DELIVERY_UNKNOWN',
       error_message = 'TELEGRAM_DELIVERY_UNKNOWN',
       next_retry_at = NULL,
       lease_owner = NULL,
       lease_expires_at = NULL,
       claimed_at = NULL
 WHERE user_id IS NOT NULL
   AND status = 'SENDING';

DELETE FROM telegram_delivery_outbox
 WHERE user_id IS NULL
   AND status = 'SENDING';

UPDATE telegram_delivery_outbox
   SET status = 'FAILED',
       error_message = 'TELEGRAM_RATE_LIMIT_EXHAUSTED',
       next_retry_at = NULL,
       lease_owner = NULL,
       lease_expires_at = NULL,
       claimed_at = NULL
 WHERE user_id IS NOT NULL
   AND status = 'PENDING'
   AND attempts >= max_attempts;

DELETE FROM telegram_delivery_outbox
 WHERE user_id IS NULL
   AND status = 'PENDING'
   AND attempts >= max_attempts;

UPDATE telegram_delivery_outbox
   SET lease_owner = NULL,
       lease_expires_at = NULL,
       claimed_at = NULL
 WHERE status <> 'SENDING';

CREATE INDEX IF NOT EXISTS idx_telegram_outbox_pending_due
    ON telegram_delivery_outbox (next_retry_at, id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_telegram_outbox_sending_expiry
    ON telegram_delivery_outbox (lease_expires_at, id)
    WHERE status = 'SENDING';

CREATE INDEX IF NOT EXISTS idx_telegram_outbox_owner_generation
    ON telegram_delivery_outbox (lease_owner, lease_generation)
    WHERE status = 'SENDING';

ALTER TABLE telegram_delivery_outbox
    ADD CONSTRAINT chk_telegram_outbox_status
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'DELIVERY_UNKNOWN')) NOT VALID,
    ADD CONSTRAINT chk_telegram_outbox_lease_generation_nonnegative
        CHECK (lease_generation >= 0) NOT VALID,
    ADD CONSTRAINT chk_telegram_outbox_sending_complete_lease
        CHECK (
            status <> 'SENDING'
            OR (
                lease_owner IS NOT NULL
                AND length(trim(lease_owner)) > 0
                AND lease_expires_at IS NOT NULL
                AND claimed_at IS NOT NULL
            )
        ) NOT VALID,
    ADD CONSTRAINT chk_telegram_outbox_non_sending_without_lease
        CHECK (
            status = 'SENDING'
            OR (lease_owner IS NULL AND lease_expires_at IS NULL AND claimed_at IS NULL)
        ) NOT VALID,
    ADD CONSTRAINT chk_telegram_outbox_pending_not_exhausted
        CHECK (status <> 'PENDING' OR attempts < max_attempts) NOT VALID,
    ADD CONSTRAINT chk_telegram_outbox_no_anonymous_terminal
        CHECK (user_id IS NOT NULL OR status IN ('PENDING', 'SENDING')) NOT VALID;

ALTER TABLE telegram_delivery_outbox
    VALIDATE CONSTRAINT chk_telegram_outbox_status,
    VALIDATE CONSTRAINT chk_telegram_outbox_lease_generation_nonnegative,
    VALIDATE CONSTRAINT chk_telegram_outbox_sending_complete_lease,
    VALIDATE CONSTRAINT chk_telegram_outbox_non_sending_without_lease,
    VALIDATE CONSTRAINT chk_telegram_outbox_pending_not_exhausted,
    VALIDATE CONSTRAINT chk_telegram_outbox_no_anonymous_terminal;
