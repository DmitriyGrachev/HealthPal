CREATE TABLE ai_usage_budget (
    scope_type  VARCHAR(16)  NOT NULL,
    scope_id    BIGINT       NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    used_tokens BIGINT       NOT NULL DEFAULT 0 CHECK (used_tokens >= 0),
    PRIMARY KEY (scope_type, scope_id, window_start),
    CONSTRAINT chk_ai_usage_budget_scope CHECK (scope_type IN ('GLOBAL', 'USER'))
);

CREATE INDEX idx_ai_usage_budget_window ON ai_usage_budget(window_start);
