-- FatSecret identifier-only storage and restricted-content cleanup.
-- V1-V31 are immutable. Apply only after the documented backup, aggregate
-- preflight, and writer/worker/replay quiescence procedure.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE fatsecret_connection
    ADD COLUMN IF NOT EXISTS connection_epoch UUID;

UPDATE fatsecret_connection
   SET connection_epoch = gen_random_uuid()
 WHERE connection_epoch IS NULL;

ALTER TABLE fatsecret_connection
    ALTER COLUMN connection_epoch SET DEFAULT gen_random_uuid(),
    ALTER COLUMN connection_epoch SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = 'fatsecret_connection'::regclass
           AND conname = 'uq_fatsecret_connection_user_epoch'
    ) THEN
        ALTER TABLE fatsecret_connection
            ADD CONSTRAINT uq_fatsecret_connection_user_epoch
                UNIQUE (user_id, connection_epoch);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS fatsecret_provider_identifiers
(
    user_id           BIGINT       NOT NULL,
    connection_epoch  UUID         NOT NULL,
    identifier_type   VARCHAR(32)  NOT NULL,
    identifier_value  VARCHAR(128) NOT NULL,
    first_received_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_received_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_fatsecret_provider_identifiers
        PRIMARY KEY (user_id, connection_epoch, identifier_type, identifier_value),
    CONSTRAINT fk_fatsecret_provider_identifier_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_fatsecret_provider_identifier_connection
        FOREIGN KEY (user_id, connection_epoch)
        REFERENCES fatsecret_connection(user_id, connection_epoch) ON DELETE CASCADE,
    CONSTRAINT chk_fatsecret_provider_identifier_type CHECK (
        identifier_type IN (
            'exercise_id',
            'food_category_id',
            'food_entry_id',
            'food_id',
            'recipe_id',
            'recipe_types',
            'saved_meal_id',
            'saved_meal_item_id',
            'serving_id'
        )
    ),
    CONSTRAINT chk_fatsecret_provider_identifier_shape CHECK (
        (identifier_type = 'recipe_types'
            AND identifier_value ~ '^[A-Za-z0-9_-]+(,[A-Za-z0-9_-]+)*$')
        OR
        (identifier_type <> 'recipe_types'
            AND identifier_value ~ '^[1-9][0-9]{0,18}$')
    ),
    CONSTRAINT chk_fatsecret_provider_identifier_receipt_order
        CHECK (last_received_at >= first_received_at)
);

CREATE INDEX IF NOT EXISTS idx_fatsecret_provider_identifier_lookup
    ON fatsecret_provider_identifiers(identifier_type, identifier_value);

COMMENT ON TABLE fatsecret_provider_identifiers IS
    'Only FatSecret fields explicitly permitted for indefinite identifier storage';

-- Capture every owner whose restricted source or connection existed before
-- removing lineage. The set is transaction-local and contains identifiers only.
CREATE TEMPORARY TABLE fatsecret_retention_affected_users
ON COMMIT DROP
AS
SELECT user_id FROM fatsecret_connection
UNION
SELECT user_id FROM fatsecret_day
UNION
SELECT user_id FROM weight_history WHERE weight_source = 'FATSECRET'
UNION
SELECT user_id FROM nutrition_source_state;

-- Preserve only the two allowed IDs available in the legacy food shape, and
-- only while a current connection exists. Names, dates, macros, and hashes are
-- never copied into the new table.
INSERT INTO fatsecret_provider_identifiers
        (user_id, connection_epoch, identifier_type, identifier_value)
SELECT DISTINCT day.user_id,
       connection.connection_epoch,
       legacy.identifier_type,
       legacy.identifier_value
  FROM fatsecret_day day
  JOIN fatsecret_food food ON food.day_id = day.id
  JOIN fatsecret_connection connection ON connection.user_id = day.user_id
 CROSS JOIN LATERAL (
     VALUES
         ('food_id'::VARCHAR(32), food.external_food_id::TEXT),
         ('food_entry_id'::VARCHAR(32), food.external_entry_id::TEXT)
 ) legacy(identifier_type, identifier_value)
 WHERE legacy.identifier_value ~ '^[1-9][0-9]{0,18}$'
ON CONFLICT DO NOTHING;

-- Conservative derived-data invalidation. Current legacy rows do not carry
-- reliable FatSecret provenance, so ambiguous projections for affected owners
-- are removed before their restricted source disappears.
DELETE FROM event_publication publication
 USING fatsecret_retention_affected_users affected
 WHERE publication.user_id = affected.user_id;

DELETE FROM durable_jobs job
 USING fatsecret_retention_affected_users affected
 WHERE job.user_id = affected.user_id
   AND job.job_type IN ('NUTRITION_SYNC', 'DAILY_INSIGHT', 'WEEKLY_REPORT', 'MONTHLY_REPORT');

DELETE FROM telegram_delivery_outbox outbox
 USING fatsecret_retention_affected_users affected
 WHERE outbox.user_id = affected.user_id;

DELETE FROM user_memory memory
 USING fatsecret_retention_affected_users affected
 WHERE memory.user_id = affected.user_id;

DELETE FROM ai_insights insight
 USING fatsecret_retention_affected_users affected
 WHERE insight.user_id = affected.user_id;

DELETE FROM nutrition_source_state;
DELETE FROM weight_history WHERE weight_source = 'FATSECRET';
DELETE FROM fatsecret_day;

-- Reject any future reintroduction through a legacy code path. DELETE remains
-- available for lifecycle cleanup and historical schema compatibility.
CREATE OR REPLACE FUNCTION fitnessapp_reject_fatsecret_restricted_content()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'FatSecret restricted content persistence is disabled'
        USING ERRCODE = '23514';
END;
$$;

DROP TRIGGER IF EXISTS trg_reject_fatsecret_day_write ON fatsecret_day;
CREATE TRIGGER trg_reject_fatsecret_day_write
    BEFORE INSERT OR UPDATE ON fatsecret_day
    FOR EACH ROW
    EXECUTE FUNCTION fitnessapp_reject_fatsecret_restricted_content();

DROP TRIGGER IF EXISTS trg_reject_fatsecret_food_write ON fatsecret_food;
CREATE TRIGGER trg_reject_fatsecret_food_write
    BEFORE INSERT OR UPDATE ON fatsecret_food
    FOR EACH ROW
    EXECUTE FUNCTION fitnessapp_reject_fatsecret_restricted_content();

ALTER TABLE weight_history
    ADD CONSTRAINT chk_weight_history_no_fatsecret_source
        CHECK (weight_source <> 'FATSECRET') NOT VALID;

ALTER TABLE weight_history
    VALIDATE CONSTRAINT chk_weight_history_no_fatsecret_source;
