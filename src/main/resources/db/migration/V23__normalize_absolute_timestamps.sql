-- Legacy naive note timestamps carry no recoverable zone; the stable baseline interprets them as UTC.
ALTER TABLE user_notes
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';
