-- Run immediately before V25 while writers are stopped.
-- The bridge keeps only aggregate/schema evidence; it never emits row content.
LOCK TABLE users,
           fatsecret_day,
           fatsecret_food,
           profile,
           weight_history,
           workout_exercises,
           workout_sets,
           user_notes,
           durable_jobs,
           telegram_delivery_outbox
    IN SHARE ROW EXCLUSIVE MODE;

WITH RECURSIVE username_candidates (id, base_value, suffix, candidate_value) AS (
    SELECT app_user.id,
           '__legacy_invalid_username_' || app_user.id::text || '__',
           0,
           '__legacy_invalid_username_' || app_user.id::text || '__'
      FROM users app_user
     WHERE app_user.username IS NULL
        OR length(trim(app_user.username)) = 0
    UNION ALL
    SELECT candidate.id,
           candidate.base_value,
           candidate.suffix + 1,
           candidate.base_value || ':' || (candidate.suffix + 1)::text
      FROM username_candidates candidate
     WHERE EXISTS (
               SELECT 1
                 FROM users existing
                WHERE existing.username = candidate.candidate_value
           )
), chosen_usernames AS (
    SELECT DISTINCT ON (id) id, candidate_value
      FROM username_candidates candidate
     WHERE NOT EXISTS (
               SELECT 1
                 FROM users existing
                WHERE existing.username = candidate.candidate_value
           )
     ORDER BY id, suffix
)
UPDATE users app_user
   SET username = chosen_usernames.candidate_value
  FROM chosen_usernames
 WHERE app_user.id = chosen_usernames.id
   AND (app_user.username IS NULL OR length(trim(app_user.username)) = 0);

WITH RECURSIVE email_candidates (id, base_value, suffix, candidate_value) AS (
    SELECT app_user.id,
           '__legacy_invalid_email_' || app_user.id::text || '__',
           0,
           '__legacy_invalid_email_' || app_user.id::text || '__'
      FROM users app_user
     WHERE app_user.email IS NULL
        OR length(trim(app_user.email)) = 0
    UNION ALL
    SELECT candidate.id,
           candidate.base_value,
           candidate.suffix + 1,
           candidate.base_value || ':' || (candidate.suffix + 1)::text
      FROM email_candidates candidate
     WHERE EXISTS (
               SELECT 1
                 FROM users existing
                WHERE existing.email = candidate.candidate_value
           )
), chosen_emails AS (
    SELECT DISTINCT ON (id) id, candidate_value
      FROM email_candidates candidate
     WHERE NOT EXISTS (
               SELECT 1
                 FROM users existing
                WHERE existing.email = candidate.candidate_value
           )
     ORDER BY id, suffix
)
UPDATE users app_user
   SET email = chosen_emails.candidate_value
  FROM chosen_emails
 WHERE app_user.id = chosen_emails.id
   AND (app_user.email IS NULL OR length(trim(app_user.email)) = 0);

UPDATE fatsecret_day
   SET calories = CASE WHEN calories < 0 THEN NULL ELSE calories END,
       protein = CASE WHEN protein < 0 THEN NULL ELSE protein END,
       fat = CASE WHEN fat < 0 THEN NULL ELSE fat END,
       carbohydrate = CASE WHEN carbohydrate < 0 THEN NULL ELSE carbohydrate END;

DELETE FROM fatsecret_food
 WHERE external_food_id IS NULL
    OR external_food_id <= 0
    OR name IS NULL
    OR length(trim(name)) = 0
    OR meal_type IS NULL
    OR length(trim(meal_type)) = 0;

UPDATE fatsecret_food
   SET calories = CASE WHEN calories < 0 THEN NULL ELSE calories END,
       protein = CASE WHEN protein < 0 THEN NULL ELSE protein END,
       fat = CASE WHEN fat < 0 THEN NULL ELSE fat END,
       carbohydrate = CASE WHEN carbohydrate < 0 THEN NULL ELSE carbohydrate END;

UPDATE profile
   SET goal_weight_kg = CASE WHEN goal_weight_kg <= 0 THEN NULL ELSE goal_weight_kg END,
       last_weight_kg = CASE WHEN last_weight_kg <= 0 THEN NULL ELSE last_weight_kg END,
       height_cm = CASE WHEN height_cm <= 0 THEN NULL ELSE height_cm END;

DELETE FROM weight_history
 WHERE weight_kg IS NULL
    OR weight_kg <= 0;

DELETE FROM workout_exercises
 WHERE exercise_name IS NULL
    OR length(trim(exercise_name)) = 0;

DELETE FROM workout_sets
 WHERE set_index IS NULL
    OR set_index < 0
    OR reps IS NULL
    OR reps < 0;

UPDATE workout_sets
   SET weight = CASE WHEN weight < 0 THEN NULL ELSE weight END;

DELETE FROM user_notes
 WHERE content IS NULL
    OR length(trim(content)) = 0;

UPDATE user_notes
   SET type = 'OTHER'
 WHERE type IS NULL
    OR type NOT IN (
        'ILLNESS', 'TRAVEL', 'INJURY', 'STRESS', 'ALLERGY', 'GOAL',
        'PREFERENCE', 'TRAINING', 'NUTRITION', 'GENERAL', 'MOOD', 'OTHER'
    );

WITH normalized AS (
    SELECT job.id,
           job.status,
           job.attempts,
           job.max_attempts,
           job.updated_at,
           CASE WHEN job.max_attempts > 0 THEN job.max_attempts ELSE 1 END AS safe_max_attempts,
           CASE
               WHEN job.attempts < 0 THEN 0
               WHEN job.max_attempts <= 0 THEN 1
               WHEN job.attempts > job.max_attempts THEN job.max_attempts
               ELSE job.attempts
           END AS safe_attempts,
           job.status NOT IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')
               OR job.attempts < 0
               OR job.max_attempts <= 0
               OR job.attempts > job.max_attempts AS invalid_state
      FROM durable_jobs job
), decisions AS (
    SELECT normalized.*,
           normalized.status IN ('PENDING', 'RUNNING')
               AND normalized.attempts >= normalized.max_attempts
               AND NOT normalized.invalid_state AS exhausted
      FROM normalized
)
UPDATE durable_jobs job
   SET attempts = decisions.safe_attempts,
       max_attempts = decisions.safe_max_attempts,
       status = CASE
                    WHEN decisions.invalid_state THEN 'FAILED'
                    WHEN decisions.exhausted THEN 'FAILED'
                    WHEN decisions.status = 'RUNNING'
                     AND decisions.updated_at < CURRENT_TIMESTAMP - INTERVAL '15 minutes' THEN 'PENDING'
                    ELSE decisions.status
                END,
       error_message = CASE
                           WHEN decisions.invalid_state THEN 'LEGACY_DURABLE_STATE_INVALID_PRE_V25'
                           WHEN decisions.exhausted THEN 'LEGACY_DURABLE_ATTEMPTS_EXHAUSTED_PRE_V25'
                           ELSE job.error_message
                       END
  FROM decisions
 WHERE job.id = decisions.id;

DELETE FROM telegram_delivery_outbox
 WHERE text IS NULL
    OR length(trim(text)) = 0;

WITH normalized AS (
    SELECT delivery.id,
           delivery.status,
           delivery.attempts,
           delivery.max_attempts,
           delivery.claimed_at,
           CASE WHEN delivery.max_attempts > 0 THEN delivery.max_attempts ELSE 1 END AS safe_max_attempts,
           CASE
               WHEN delivery.attempts < 0 THEN 0
               WHEN delivery.max_attempts <= 0 THEN 1
               WHEN delivery.attempts > delivery.max_attempts THEN delivery.max_attempts
               ELSE delivery.attempts
           END AS safe_attempts,
           delivery.status NOT IN ('PENDING', 'SENDING', 'SENT', 'FAILED')
               OR delivery.attempts < 0
               OR delivery.max_attempts <= 0
               OR delivery.attempts > delivery.max_attempts AS invalid_state
      FROM telegram_delivery_outbox delivery
), decisions AS (
    SELECT normalized.*,
           normalized.status IN ('PENDING', 'SENDING')
               AND normalized.attempts >= normalized.max_attempts
               AND NOT normalized.invalid_state AS exhausted,
           normalized.status = 'SENDING' AND normalized.claimed_at IS NULL AS unknown_outcome
      FROM normalized
)
UPDATE telegram_delivery_outbox delivery
   SET attempts = decisions.safe_attempts,
       max_attempts = decisions.safe_max_attempts,
       status = CASE
                    WHEN decisions.unknown_outcome THEN 'FAILED'
                    WHEN decisions.invalid_state THEN 'FAILED'
                    WHEN decisions.exhausted THEN 'FAILED'
                    ELSE decisions.status
                END,
       error_message = CASE
                           WHEN decisions.unknown_outcome THEN 'LEGACY_DELIVERY_OUTCOME_UNKNOWN_PRE_V25'
                           WHEN decisions.invalid_state THEN 'LEGACY_DELIVERY_STATE_INVALID_PRE_V25'
                           WHEN decisions.exhausted THEN 'LEGACY_DELIVERY_ATTEMPTS_EXHAUSTED_PRE_V25'
                           ELSE delivery.error_message
                       END
  FROM decisions
 WHERE delivery.id = decisions.id;
