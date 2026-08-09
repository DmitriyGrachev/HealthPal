ALTER TABLE users
    ADD COLUMN IF NOT EXISTS time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC';

ALTER TABLE users
    ADD CONSTRAINT chk_users_time_zone_non_blank CHECK (length(trim(time_zone)) > 0);
