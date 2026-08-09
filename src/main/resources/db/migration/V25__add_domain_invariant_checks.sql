-- Keep basic domain invariants enforced even when writes bypass the application layer.
ALTER TABLE users
    ADD CONSTRAINT chk_users_username_non_blank CHECK (length(trim(username)) > 0),
    ADD CONSTRAINT chk_users_email_non_blank CHECK (length(trim(email)) > 0);

ALTER TABLE fatsecret_day
    ADD CONSTRAINT chk_fatsecret_day_non_negative CHECK (
        (calories IS NULL OR calories >= 0)
        AND (protein IS NULL OR protein >= 0)
        AND (fat IS NULL OR fat >= 0)
        AND (carbohydrate IS NULL OR carbohydrate >= 0)
    );

ALTER TABLE fatsecret_food
    ADD CONSTRAINT chk_fatsecret_food_positive_id CHECK (external_food_id > 0),
    ADD CONSTRAINT chk_fatsecret_food_non_negative CHECK (
        (calories IS NULL OR calories >= 0)
        AND (protein IS NULL OR protein >= 0)
        AND (fat IS NULL OR fat >= 0)
        AND (carbohydrate IS NULL OR carbohydrate >= 0)
    ),
    ADD CONSTRAINT chk_fatsecret_food_name_non_blank CHECK (length(trim(name)) > 0),
    ADD CONSTRAINT chk_fatsecret_food_meal_non_blank CHECK (length(trim(meal_type)) > 0);

ALTER TABLE profile
    ADD CONSTRAINT chk_profile_positive_measurements CHECK (
        (goal_weight_kg IS NULL OR goal_weight_kg > 0)
        AND (last_weight_kg IS NULL OR last_weight_kg > 0)
        AND (height_cm IS NULL OR height_cm > 0)
    );

ALTER TABLE weight_history
    ADD CONSTRAINT chk_weight_history_positive_weight CHECK (weight_kg > 0);

ALTER TABLE workout_exercises
    ADD CONSTRAINT chk_workout_exercise_name_non_blank CHECK (length(trim(exercise_name)) > 0);

ALTER TABLE workout_sets
    ADD CONSTRAINT chk_workout_set_non_negative CHECK (
        set_index >= 0
        AND reps >= 0
        AND (weight IS NULL OR weight >= 0)
    );

ALTER TABLE user_notes
    ADD CONSTRAINT chk_user_note_content_non_blank CHECK (length(trim(content)) > 0),
    ADD CONSTRAINT chk_user_note_type CHECK (type IN (
        'ILLNESS', 'TRAVEL', 'INJURY', 'STRESS', 'ALLERGY', 'GOAL', 'PREFERENCE',
        'TRAINING', 'NUTRITION', 'GENERAL', 'MOOD', 'OTHER'
    ));

ALTER TABLE durable_jobs
    ADD CONSTRAINT chk_durable_job_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    ADD CONSTRAINT chk_durable_job_attempts CHECK (attempts >= 0 AND max_attempts > 0 AND attempts <= max_attempts);

ALTER TABLE telegram_delivery_outbox
    ADD CONSTRAINT chk_telegram_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    ADD CONSTRAINT chk_telegram_outbox_attempts CHECK (attempts >= 0 AND max_attempts > 0 AND attempts <= max_attempts),
    ADD CONSTRAINT chk_telegram_outbox_text_non_blank CHECK (length(trim(text)) > 0);
