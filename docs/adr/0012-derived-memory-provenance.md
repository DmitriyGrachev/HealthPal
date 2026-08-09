# ADR 0012: Derived Memory Provenance

## Decision

Insight and note memories carry a source key, source type/ID, snapshot hash where applicable, and a deterministic UUID used as the pgvector row ID. Source deletion publishes a deletion event that removes the derived row. Insight regeneration deletes/replaces the same deterministic row.

## Consequences

- Rebuilds are idempotent and stale derived rows are addressable.
- Human-readable source keys remain in metadata while PostgreSQL receives valid UUID IDs.
- Existing legacy rows require a one-time rebuild before enabling approximate vector indexes.
