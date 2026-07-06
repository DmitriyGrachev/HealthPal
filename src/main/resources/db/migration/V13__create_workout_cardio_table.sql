CREATE SEQUENCE IF NOT EXISTS workout_cardio_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS workout_cardio
(
    id               BIGINT           NOT NULL DEFAULT nextval('workout_cardio_seq') PRIMARY KEY,
    jefit_id         BIGINT           NOT NULL,
    user_id          BIGINT           NOT NULL,
    date             TIMESTAMP        NOT NULL,
    exercise_id      BIGINT,
    exercise_name    VARCHAR(255),
    duration_seconds INTEGER          NOT NULL DEFAULT 0,
    distance         DOUBLE PRECISION NOT NULL DEFAULT 0,
    calories         DOUBLE PRECISION NOT NULL DEFAULT 0,

    CONSTRAINT uq_workout_cardio_jefit_user UNIQUE (jefit_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_workout_cardio_user_date
    ON workout_cardio (user_id, date);
