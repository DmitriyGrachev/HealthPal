-- V14__align_schema_and_fks.sql
-- FND-002: Align users table column lengths and unique constraints
ALTER TABLE users ALTER COLUMN username TYPE VARCHAR(64);
ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_users_email'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
    END IF;
END $$;

-- FND-003: Ownership schema foreign keys and NOT NULL constraints
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_fatsecret_day_user'
    ) THEN
        ALTER TABLE fatsecret_day ADD CONSTRAINT fk_fatsecret_day_user
            FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_workout_user'
    ) THEN
        ALTER TABLE workout ADD CONSTRAINT fk_workout_user
            FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_workout_cardio_user'
    ) THEN
        ALTER TABLE workout_cardio ADD CONSTRAINT fk_workout_cardio_user
            FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
    END IF;
END $$;

ALTER TABLE fatsecret_food ALTER COLUMN day_id SET NOT NULL;
ALTER TABLE workout_exercises ALTER COLUMN workout_id SET NOT NULL;
ALTER TABLE workout_sets ALTER COLUMN exercise_id SET NOT NULL;
