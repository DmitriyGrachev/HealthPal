-- Do not pretend that old usage rows identify a historical version: leave them unknown.
ALTER TABLE knowledge_claim_usage
    ADD COLUMN claim_version BIGINT,
    ADD COLUMN claim_content_hash VARCHAR(64),
    ADD CONSTRAINT chk_knowledge_claim_usage_identity CHECK (
        (claim_version IS NULL AND claim_content_hash IS NULL)
        OR (claim_version IS NOT NULL AND claim_version >= 0
            AND claim_content_hash IS NOT NULL AND claim_content_hash ~ '^[0-9a-f]{64}$'));
