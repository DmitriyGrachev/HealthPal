-- Versioned, owner-bound source truth. V1-V30 remain immutable.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS lifecycle_epoch UUID;

UPDATE users
   SET lifecycle_epoch = gen_random_uuid()
 WHERE lifecycle_epoch IS NULL;

ALTER TABLE users
    ALTER COLUMN lifecycle_epoch SET DEFAULT gen_random_uuid(),
    ALTER COLUMN lifecycle_epoch SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_users_lifecycle_epoch'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT uq_users_lifecycle_epoch UNIQUE (lifecycle_epoch);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_users_id_lifecycle_epoch'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT uq_users_id_lifecycle_epoch UNIQUE (id, lifecycle_epoch);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS nutrition_source_state
(
    user_id         BIGINT       NOT NULL,
    source_date     DATE         NOT NULL,
    source_version  BIGINT       NOT NULL,
    content_hash    VARCHAR(64)  NOT NULL,
    present         BOOLEAN      NOT NULL,
    lifecycle_epoch UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT current_timestamp,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT current_timestamp,

    CONSTRAINT pk_nutrition_source_state PRIMARY KEY (user_id, source_date),
    CONSTRAINT fk_nutrition_source_state_user_epoch
        FOREIGN KEY (user_id, lifecycle_epoch)
        REFERENCES users (id, lifecycle_epoch) ON DELETE CASCADE,
    CONSTRAINT chk_nutrition_source_state_version_positive CHECK (source_version > 0),
    CONSTRAINT chk_nutrition_source_state_hash
        CHECK (content_hash ~ '^[0-9a-f]{64}$' AND content_hash = lower(content_hash)),
    CONSTRAINT chk_nutrition_source_state_timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX IF NOT EXISTS idx_nutrition_source_state_date
    ON nutrition_source_state (user_id, source_date, source_version);

CREATE TABLE IF NOT EXISTS workout_source_state
(
    user_id         BIGINT       NOT NULL,
    source_date     DATE         NOT NULL,
    source_version  BIGINT       NOT NULL,
    content_hash    VARCHAR(64)  NOT NULL,
    present         BOOLEAN      NOT NULL,
    lifecycle_epoch UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT current_timestamp,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT current_timestamp,

    CONSTRAINT pk_workout_source_state PRIMARY KEY (user_id, source_date),
    CONSTRAINT fk_workout_source_state_user_epoch
        FOREIGN KEY (user_id, lifecycle_epoch)
        REFERENCES users (id, lifecycle_epoch) ON DELETE CASCADE,
    CONSTRAINT chk_workout_source_state_version_positive CHECK (source_version > 0),
    CONSTRAINT chk_workout_source_state_hash
        CHECK (content_hash ~ '^[0-9a-f]{64}$' AND content_hash = lower(content_hash)),
    CONSTRAINT chk_workout_source_state_timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX IF NOT EXISTS idx_workout_source_state_date
    ON workout_source_state (user_id, source_date, source_version);

CREATE OR REPLACE FUNCTION fitnessapp_enforce_source_version_monotonic()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.source_version <= OLD.source_version THEN
        RAISE EXCEPTION 'source_version must increase monotonically';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_nutrition_source_version_monotonic ON nutrition_source_state;
CREATE TRIGGER trg_nutrition_source_version_monotonic
    BEFORE UPDATE ON nutrition_source_state
    FOR EACH ROW
    EXECUTE FUNCTION fitnessapp_enforce_source_version_monotonic();

DROP TRIGGER IF EXISTS trg_workout_source_version_monotonic ON workout_source_state;
CREATE TRIGGER trg_workout_source_version_monotonic
    BEFORE UPDATE ON workout_source_state
    FOR EACH ROW
    EXECUTE FUNCTION fitnessapp_enforce_source_version_monotonic();

-- Backfill only stable identifiers and a deterministic digest of canonical
-- redacted summaries. Provider names, notes, and exercise text never enter
-- state; existing summary/entry hashes, numeric fields, and stable identifiers
-- are retained in the digest. User/date are source-state metadata, not content.
INSERT INTO nutrition_source_state
        (user_id, source_date, source_version, content_hash, present, lifecycle_epoch, created_at, updated_at)
SELECT day.user_id,
       day.date,
       1,
       encode(digest(concat_ws('|', 'NUTRITION_DAY',
                               coalesce(day.summary_hash, 'null'),
                               coalesce(day.entries_hash, 'null'),
                               coalesce(day.version::text, 'null'),
                               coalesce(day.calories::text, 'null'),
                               coalesce(day.protein::text, 'null'),
                               coalesce(day.fat::text, 'null'),
                               coalesce(day.carbohydrate::text, 'null'),
                               coalesce(entries.entry_summary, 'null')),
                      'sha256'), 'hex'),
       TRUE,
       app_user.lifecycle_epoch,
       current_timestamp,
       current_timestamp
  FROM fatsecret_day day
  LEFT JOIN LATERAL (
      SELECT string_agg(
                 concat_ws(':', food.external_food_id::text,
                           coalesce(food.external_entry_id::text, 'null'),
                           coalesce(food.calories::text, 'null'),
                           coalesce(food.protein::text, 'null'),
                           coalesce(food.fat::text, 'null'),
                           coalesce(food.carbohydrate::text, 'null')),
                 '|' ORDER BY food.external_food_id,
                                  food.external_entry_id NULLS FIRST,
                                  food.calories,
                                  food.protein,
                                  food.fat,
                                  food.carbohydrate) AS entry_summary
        FROM fatsecret_food food
       WHERE food.day_id = day.id
  ) entries ON TRUE
  JOIN users app_user ON app_user.id = day.user_id
 ON CONFLICT (user_id, source_date) DO NOTHING;

WITH strength_sessions AS (
    SELECT user_id,
           date::date AS source_date,
           string_agg(concat_ws(':', coalesce(jefit_id::text, 'null')),
                      '|' ORDER BY jefit_id NULLS FIRST) AS session_summary
      FROM workout
     WHERE date IS NOT NULL
     GROUP BY user_id, date::date
), strength_sets AS (
    SELECT workout.user_id,
           workout.date::date AS source_date,
           string_agg(concat_ws(':', coalesce(exercise.jefit_log_id::text, 'null'),
                                sets.set_index::text,
                                coalesce(sets.weight::text, 'null'), sets.reps::text),
                      '|' ORDER BY exercise.jefit_log_id NULLS FIRST,
                                        sets.set_index,
                                        sets.weight,
                                        sets.reps) AS set_summary
      FROM workout
      JOIN workout_exercises exercise ON exercise.workout_id = workout.id
      JOIN workout_sets sets ON sets.exercise_id = exercise.id
     WHERE workout.date IS NOT NULL
     GROUP BY workout.user_id, workout.date::date
), strength_daily AS (
    SELECT sessions.user_id,
           sessions.source_date,
           concat_ws('|', sessions.session_summary, sets.set_summary) AS strength_summary
      FROM strength_sessions sessions
      LEFT JOIN strength_sets sets
        ON sets.user_id = sessions.user_id AND sets.source_date = sessions.source_date
), cardio_daily AS (
    SELECT user_id,
           date::date AS source_date,
           string_agg(concat_ws(':', jefit_id::text,
                                coalesce(exercise_id::text, 'null'), duration_seconds::text,
                                distance::text, calories::text),
                      '|' ORDER BY jefit_id,
                                        exercise_id NULLS FIRST,
                                        duration_seconds,
                                        distance,
                                        calories) AS cardio_summary
      FROM workout_cardio
     WHERE date IS NOT NULL
     GROUP BY user_id, date::date
), workout_dates AS (
    SELECT user_id, source_date FROM strength_daily
    UNION
    SELECT user_id, source_date FROM cardio_daily
)
INSERT INTO workout_source_state
        (user_id, source_date, source_version, content_hash, present, lifecycle_epoch, created_at, updated_at)
SELECT dates.user_id,
       dates.source_date,
       1,
       encode(digest(concat_ws('|', 'WORKOUT_DAY',
                              coalesce(strength.strength_summary, 'null'),
                              coalesce(cardio.cardio_summary, 'null')),
                     'sha256'), 'hex'),
       TRUE,
       app_user.lifecycle_epoch,
       current_timestamp,
       current_timestamp
  FROM workout_dates dates
  LEFT JOIN strength_daily strength
    ON strength.user_id = dates.user_id AND strength.source_date = dates.source_date
  LEFT JOIN cardio_daily cardio
    ON cardio.user_id = dates.user_id AND cardio.source_date = dates.source_date
  JOIN users app_user ON app_user.id = dates.user_id
 ON CONFLICT (user_id, source_date) DO NOTHING;

-- Remove only exact legacy nutrition/workout publications with malformed or
-- incomplete metadata. The MATERIALIZED CTE and CASE ensure malformed text is
-- never cast to jsonb. Complete metadata is retained only when all fields have
-- the types, ranges, enum values, hash/UUID grammar, and ISO date/instant
-- shapes accepted by DomainEventMetadata.
WITH legacy_publications AS MATERIALIZED (
    SELECT publication.id,
           publication.event_type,
           publication.user_id,
           CASE WHEN publication.serialized_event IS JSON
                THEN publication.serialized_event::jsonb
                ELSE NULL::jsonb
           END AS payload
      FROM event_publication publication
     WHERE publication.event_type IN (
         'com.fit.fitnessapp.api.NutritionSyncedEvent',
         'com.fit.fitnessapp.api.WorkoutImportedEvent')
), metadata_values AS MATERIALIZED (
    SELECT legacy.id,
           legacy.event_type,
           legacy.payload,
           legacy.user_id AS publication_user_id,
           legacy.payload -> 'metadata' AS metadata,
           jsonb_typeof(legacy.payload -> 'userId') AS payload_user_id_type,
           jsonb_typeof(legacy.payload #> '{metadata,eventId}') AS event_id_type,
           legacy.payload #>> '{metadata,eventId}' AS event_id,
           jsonb_typeof(legacy.payload #> '{metadata,userId}') AS user_id_type,
           legacy.payload #>> '{metadata,userId}' AS user_id,
           jsonb_typeof(legacy.payload #> '{metadata,sourceType}') AS source_type_type,
           legacy.payload #>> '{metadata,sourceType}' AS source_type,
           jsonb_typeof(legacy.payload #> '{metadata,sourceId}') AS source_id_type,
           legacy.payload #>> '{metadata,sourceId}' AS source_id,
           jsonb_typeof(legacy.payload #> '{metadata,sourceVersion}') AS source_version_type,
           legacy.payload #>> '{metadata,sourceVersion}' AS source_version,
           jsonb_typeof(legacy.payload #> '{metadata,changeType}') AS change_type_type,
           legacy.payload #>> '{metadata,changeType}' AS change_type,
           jsonb_typeof(legacy.payload #> '{metadata,contentHash}') AS content_hash_type,
           legacy.payload #>> '{metadata,contentHash}' AS content_hash,
           jsonb_typeof(legacy.payload #> '{metadata,lifecycleEpoch}') AS lifecycle_epoch_type,
           legacy.payload #>> '{metadata,lifecycleEpoch}' AS lifecycle_epoch,
           jsonb_typeof(legacy.payload #> '{metadata,schemaVersion}') AS schema_version_type,
           legacy.payload #>> '{metadata,schemaVersion}' AS schema_version,
           jsonb_typeof(legacy.payload #> '{metadata,occurredAt}') AS occurred_at_type,
           legacy.payload #>> '{metadata,occurredAt}' AS occurred_at
      FROM legacy_publications legacy
), invalid_publications AS (
    SELECT id
      FROM metadata_values
     WHERE payload IS NULL
        OR jsonb_typeof(payload) IS DISTINCT FROM 'object'
         OR metadata IS NULL
         OR jsonb_typeof(metadata) IS DISTINCT FROM 'object'
        OR NOT (metadata ?& ARRAY[
             'eventId', 'userId', 'sourceType', 'sourceId', 'sourceVersion',
             'changeType', 'contentHash', 'lifecycleEpoch', 'schemaVersion', 'occurredAt'
        ])
        OR payload_user_id_type IS DISTINCT FROM 'number'
        OR publication_user_id IS NULL
         OR publication_user_id::text IS DISTINCT FROM user_id
        OR event_id_type <> 'string'
        OR event_id !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
        OR NOT (
             user_id_type = 'number'
             AND COALESCE(pg_input_is_valid(user_id, 'bigint'), FALSE)
             AND user_id ~ '^[1-9][0-9]*$'
             AND (length(user_id) < 19
                  OR (length(user_id) = 19 AND user_id <= '9223372036854775807'))
        )
        OR source_type_type <> 'string'
        OR source_type NOT IN ('NUTRITION_DAY', 'WORKOUT_DAY')
        OR (event_type = 'com.fit.fitnessapp.api.NutritionSyncedEvent'
            AND source_type <> 'NUTRITION_DAY')
        OR (event_type = 'com.fit.fitnessapp.api.WorkoutImportedEvent'
            AND source_type <> 'WORKOUT_DAY')
        OR source_id_type <> 'string'
        OR source_id !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
        OR NOT COALESCE(pg_input_is_valid(source_id, 'date'), FALSE)
        OR NOT (
             source_version_type = 'number'
             AND COALESCE(pg_input_is_valid(source_version, 'bigint'), FALSE)
             AND source_version ~ '^[1-9][0-9]*$'
             AND (length(source_version) < 19
                  OR (length(source_version) = 19 AND source_version <= '9223372036854775807'))
        )
        OR change_type_type <> 'string'
        OR change_type NOT IN ('UPSERT', 'DELETE')
        OR content_hash_type <> 'string'
        OR content_hash !~ '^[0-9a-f]{64}$'
        OR lifecycle_epoch_type <> 'string'
        OR lifecycle_epoch !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
        OR NOT (
             schema_version_type = 'number'
             AND COALESCE(pg_input_is_valid(schema_version, 'integer'), FALSE)
             AND schema_version ~ '^[1-9][0-9]*$'
             AND (length(schema_version) < 10
                  OR (length(schema_version) = 10 AND schema_version <= '2147483647'))
        )
        OR occurred_at_type <> 'string'
        OR occurred_at !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{1,9})?(Z|[+-][0-9]{2}:[0-9]{2})$'
         OR NOT COALESCE(pg_input_is_valid(occurred_at, 'timestamptz'), FALSE)
)
DELETE FROM event_publication publication
 USING invalid_publications invalid
 WHERE publication.id = invalid.id;
