ALTER TABLE workout_exercises
    DROP CONSTRAINT IF EXISTS uq_exercise_jefit_log;

ALTER TABLE workout_exercises
    ADD CONSTRAINT uq_exercise_jefit_log_workout UNIQUE (jefit_log_id, workout_id);
