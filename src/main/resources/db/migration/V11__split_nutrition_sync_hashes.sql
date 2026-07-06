ALTER TABLE fatsecret_day
    ADD COLUMN IF NOT EXISTS summary_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS entries_hash VARCHAR(64);

UPDATE fatsecret_day
SET summary_hash = COALESCE(summary_hash, external_hash),
    entries_hash = COALESCE(entries_hash, external_hash)
WHERE external_hash IS NOT NULL;
