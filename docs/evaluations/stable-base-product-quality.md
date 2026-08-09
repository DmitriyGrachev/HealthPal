# Stable Base Product-Quality Evaluation

## Scope

The evaluation covers the two user-visible paths required before feature expansion:

1. FatSecret sync (provider boundary mocked) -> transactional `NutritionSyncedEvent` -> durable daily-insight job -> `InsightGeneratedEvent` contract -> pgvector memory provenance -> Telegram outbox enqueue and worker delivery.
2. Telegram `/weight` command -> transactional event publication -> PostgreSQL weight save listener -> Telegram confirmation outbox.

The provider calls are mocked by design. The evaluation verifies persistence, ownership, idempotency, asynchronous boundaries, delivery status, and recovery state against PostgreSQL Testcontainers.

## Evidence

- `StableBaseWorkflowIntegrationTest` verifies both paths end to end with real Flyway schema, durable jobs, event publications, pgvector rows, and Telegram outbox status.
- `UserDataLifecycleServiceIntegrationTest` verifies complete export/deletion isolation.
- `MemoryPgVectorIntegrationTest` verifies user isolation, expiry cleanup, UUID-safe provenance IDs, source deletion, and the HNSW dimension constraint.
- `TelegramOutboxConcurrencyIntegrationTest` verifies concurrent claim isolation and retry semantics.
- `AbsoluteTimestampPostgresIntegrationTest` verifies DST-overlap instant round trips.
- The release commands are `mvn test`, `mvn test -Parchitecture`, and `mvn verify -Pintegration`.

## Residual Product Decision

Approximate vector indexing is intentionally not enabled. The current embedding dimension is incompatible with HNSW; an IVFFlat/halfvec/lower-dimension choice requires a production-shaped corpus and latency budget before changing the runtime configuration.
