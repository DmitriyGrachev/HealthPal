-- V16__create_durable_jobs.sql
-- Durable Job Lifecycle table for asynchronous background tasks, retries, and operator recovery

CREATE TABLE IF NOT EXISTS durable_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_type VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP WITH TIME ZONE NULL,
    error_message TEXT NULL,
    payload_json TEXT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_durable_jobs_status_retry ON durable_jobs(status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_durable_jobs_user_id ON durable_jobs(user_id);
