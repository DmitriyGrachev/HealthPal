CREATE TABLE memory_projection_generations (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    projection_kind VARCHAR(32) NOT NULL DEFAULT 'KNOWLEDGE_CLAIM' CHECK (projection_kind = 'KNOWLEDGE_CLAIM'),
    status VARCHAR(16) NOT NULL CHECK (status IN ('BUILDING', 'ACTIVE', 'FAILED')),
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    base_generation_id UUID,
    job_id BIGINT,
    lease_generation BIGINT,
    failure_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    UNIQUE (user_id, id)
);
CREATE UNIQUE INDEX uq_memory_projection_active
    ON memory_projection_generations(user_id, projection_kind) WHERE status = 'ACTIVE';
CREATE INDEX idx_memory_projection_owner_status ON memory_projection_generations(user_id, status);

-- These are rebuildable projections, not canonical Goals or verified Claims.
DELETE FROM user_memory
 WHERE metadata ->> 'memory_type' = 'FACT'
   AND (metadata ? 'insight_type' OR metadata ->> 'source_type' = 'AI_INSIGHT' OR metadata ->> 'note_type' = 'GOAL');

ALTER TABLE user_memory
    ADD COLUMN projection_generation_id UUID GENERATED ALWAYS AS
        (CASE WHEN metadata ->> 'projection_kind' = 'KNOWLEDGE_CLAIM' THEN (metadata ->> 'projection_generation')::uuid END) STORED,
    ADD COLUMN projection_claim_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN metadata ->> 'projection_kind' = 'KNOWLEDGE_CLAIM' THEN (metadata ->> 'claim_id')::bigint END) STORED,
    ADD COLUMN projection_source_type TEXT GENERATED ALWAYS AS
        (CASE WHEN metadata ->> 'projection_kind' = 'KNOWLEDGE_CLAIM' THEN metadata ->> 'source_type' END) STORED,
    ADD COLUMN projection_source_id TEXT GENERATED ALWAYS AS
        (CASE WHEN metadata ->> 'projection_kind' = 'KNOWLEDGE_CLAIM' THEN metadata ->> 'source_id' END) STORED,
    ADD COLUMN projection_source_version BIGINT GENERATED ALWAYS AS
        (CASE WHEN metadata ->> 'projection_kind' = 'KNOWLEDGE_CLAIM' THEN (metadata ->> 'source_version')::bigint END) STORED,
    ADD COLUMN projection_aggregate_version BIGINT GENERATED ALWAYS AS
        (CASE WHEN metadata ->> 'projection_kind' = 'KNOWLEDGE_CLAIM' THEN (metadata ->> 'aggregate_version')::bigint END) STORED,
    ADD COLUMN projection_content_hash TEXT GENERATED ALWAYS AS
        (CASE WHEN metadata ->> 'projection_kind' = 'KNOWLEDGE_CLAIM' THEN metadata ->> 'content_hash' END) STORED,
    ADD COLUMN projection_schema_version INTEGER GENERATED ALWAYS AS
        (CASE WHEN metadata ->> 'projection_kind' = 'KNOWLEDGE_CLAIM' THEN (metadata ->> 'projection_schema')::integer END) STORED,
    ADD CONSTRAINT fk_memory_projection_generation FOREIGN KEY (user_id, projection_generation_id)
        REFERENCES memory_projection_generations(user_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_memory_projection_claim FOREIGN KEY (user_id, projection_claim_id)
        REFERENCES knowledge_claims(user_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT chk_memory_projection_metadata CHECK (
        (projection_generation_id IS NULL AND projection_claim_id IS NULL AND NOT (metadata ? 'projection_kind'))
        OR (projection_generation_id IS NOT NULL AND projection_claim_id IS NOT NULL
            AND metadata ->> 'projection_kind' = 'KNOWLEDGE_CLAIM'
            AND (metadata ->> 'memory_type') IS NOT DISTINCT FROM 'SEMANTIC'
            AND projection_source_type IS NOT NULL AND projection_source_id IS NOT NULL
            AND projection_source_version IS NOT NULL AND projection_source_version > 0
            AND projection_aggregate_version IS NOT NULL AND projection_aggregate_version >= 0
            AND projection_content_hash IS NOT NULL AND projection_content_hash ~ '^[0-9a-f]{64}$'
            AND projection_schema_version IS NOT NULL AND projection_schema_version = 1));
CREATE UNIQUE INDEX uq_memory_projection_claim ON user_memory(projection_generation_id, projection_claim_id)
    WHERE projection_generation_id IS NOT NULL;
CREATE INDEX idx_memory_projection_source ON user_memory(user_id, projection_source_type, projection_source_id, projection_source_version)
    WHERE projection_generation_id IS NOT NULL;
CREATE INDEX idx_memory_projection_owned_claim ON user_memory(user_id, projection_claim_id)
    WHERE projection_claim_id IS NOT NULL;

-- Embedding I/O finishes before VectorStore issues SQL. Fence that SQL against
-- concurrent owner deletion, Claim correction/forget and generation retirement.
CREATE FUNCTION enforce_memory_projection_source() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE
    owner_id BIGINT;
BEGIN
    IF NEW.metadata ->> 'projection_kind' IS DISTINCT FROM 'KNOWLEDGE_CLAIM' THEN
        RETURN NEW;
    END IF;
    owner_id := (NEW.metadata ->> 'user_id')::bigint;
    PERFORM id FROM users WHERE id = owner_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'PROJECTION_OWNER_MISSING' USING ERRCODE = '23503';
    END IF;
    PERFORM id FROM memory_projection_generations
     WHERE id = (NEW.metadata ->> 'projection_generation')::uuid AND user_id = owner_id
       AND status IN ('BUILDING', 'ACTIVE') AND schema_version = 1;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'PROJECTION_GENERATION_STALE' USING ERRCODE = '23514';
    END IF;
    PERFORM id FROM knowledge_claims
     WHERE id = (NEW.metadata ->> 'claim_id')::bigint AND user_id = owner_id
       AND source_type = NEW.metadata ->> 'source_type' AND source_id = NEW.metadata ->> 'source_id'
       AND source_version = (NEW.metadata ->> 'source_version')::bigint
       AND aggregate_version = (NEW.metadata ->> 'aggregate_version')::bigint
       AND content_hash = NEW.metadata ->> 'content_hash' AND schema_version = 1
       AND origin = NEW.metadata ->> 'claim_origin' AND verification = NEW.metadata ->> 'claim_verification'
       AND temporal_status = 'ACTIVE' AND verification NOT IN ('DISPUTED', 'REFUTED')
       AND observed_at <= clock_timestamp()
       AND (valid_from IS NULL OR valid_from <= clock_timestamp())
       AND (valid_until IS NULL OR valid_until > clock_timestamp());
    IF NOT FOUND THEN
        RAISE EXCEPTION 'PROJECTION_SOURCE_STALE' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_memory_projection_source BEFORE INSERT OR UPDATE ON user_memory
    FOR EACH ROW EXECUTE FUNCTION enforce_memory_projection_source();

-- Canonical mutations invalidate all generations synchronously. A delayed event
-- is never the privacy boundary; DELETE is covered by the composite FK cascade.
CREATE FUNCTION invalidate_changed_claim_projections() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    DELETE FROM user_memory WHERE user_id = NEW.user_id AND projection_claim_id = NEW.id;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_claim_projection_invalidation AFTER UPDATE ON knowledge_claims
    FOR EACH ROW EXECUTE FUNCTION invalidate_changed_claim_projections();
