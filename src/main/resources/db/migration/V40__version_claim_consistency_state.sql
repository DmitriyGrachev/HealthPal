ALTER TABLE knowledge_claim_conflicts
    ADD COLUMN aggregate_version BIGINT NOT NULL DEFAULT 0 CHECK (aggregate_version >= 0),
    ADD COLUMN left_version BIGINT NOT NULL DEFAULT 0 CHECK (left_version >= 0),
    ADD COLUMN right_version BIGINT NOT NULL DEFAULT 0 CHECK (right_version >= 0),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    DROP CONSTRAINT chk_knowledge_claim_conflicts_status;
ALTER TABLE knowledge_claim_conflicts ADD CONSTRAINT chk_knowledge_claim_conflicts_status
    CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'DISMISSED', 'RESOLVED'));

-- Canonical pair order; collapse duplicate derived notifications, never Claim content.
UPDATE knowledge_claim_conflicts
   SET left_claim_id = LEAST(left_claim_id, right_claim_id), right_claim_id = GREATEST(left_claim_id, right_claim_id);
UPDATE knowledge_claim_conflicts c SET status = 'OPEN'
 WHERE EXISTS (SELECT 1 FROM knowledge_claim_conflicts other
     WHERE other.user_id = c.user_id AND other.left_claim_id = c.left_claim_id
       AND other.right_claim_id = c.right_claim_id AND other.reason = c.reason AND other.status = 'OPEN');
DELETE FROM knowledge_claim_conflicts duplicate USING knowledge_claim_conflicts keeper
 WHERE duplicate.user_id = keeper.user_id AND duplicate.left_claim_id = keeper.left_claim_id
   AND duplicate.right_claim_id = keeper.right_claim_id AND duplicate.reason = keeper.reason AND duplicate.id > keeper.id;
UPDATE knowledge_claim_conflicts c SET left_version = l.aggregate_version, right_version = r.aggregate_version
 FROM knowledge_claims l, knowledge_claims r
 WHERE l.user_id = c.user_id AND l.id = c.left_claim_id AND r.user_id = c.user_id AND r.id = c.right_claim_id;
ALTER TABLE knowledge_claim_conflicts
    ADD CONSTRAINT uq_knowledge_conflict_pair UNIQUE (user_id, left_claim_id, right_claim_id, reason),
    ADD CONSTRAINT uq_knowledge_conflict_owner_id UNIQUE (user_id, id),
    ADD CONSTRAINT chk_knowledge_conflict_order CHECK (left_claim_id < right_claim_id);
CREATE INDEX idx_knowledge_conflict_left ON knowledge_claim_conflicts(user_id, left_claim_id);
CREATE INDEX idx_knowledge_conflict_right ON knowledge_claim_conflicts(user_id, right_claim_id);

CREATE TABLE knowledge_conflict_command_receipts (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_key_hash CHAR(64) NOT NULL CHECK (request_key_hash ~ '^[0-9a-f]{64}$'),
    conflict_id BIGINT NOT NULL,
    expected_version BIGINT NOT NULL CHECK (expected_version >= 0),
    action VARCHAR(32) NOT NULL CHECK (action IN ('ACKNOWLEDGED', 'DISMISSED')),
    result_version BIGINT NOT NULL CHECK (result_version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, request_key_hash),
    FOREIGN KEY (user_id, conflict_id) REFERENCES knowledge_claim_conflicts(user_id, id) ON DELETE CASCADE
);
CREATE INDEX idx_knowledge_conflict_receipt_target ON knowledge_conflict_command_receipts(user_id, conflict_id);

CREATE TABLE knowledge_claim_drift (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    claim_id BIGINT NOT NULL,
    reason VARCHAR(32) NOT NULL CHECK (reason IN ('SOURCE_STALE', 'SOURCE_DELETED', 'VALIDITY_EXPIRED', 'SUPERSEDED')),
    claim_version BIGINT NOT NULL CHECK (claim_version >= 0),
    detected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, claim_id, reason),
    FOREIGN KEY (user_id, claim_id) REFERENCES knowledge_claims(user_id, id) ON DELETE CASCADE
);
