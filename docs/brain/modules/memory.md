---
type: module
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../src/main/java/com/fit/fitnessapp/memory/
  - ../../src/test/java/com/fit/fitnessapp/memory/
  - ../../BACKLOG.md
  - ../../TESTING.md
tags:
  - fitnessapp
  - module
  - memory
---

# Memory Module

## What It Knows

Memory owns user memory storage, retrieval, pgvector integration, expiration metadata, event ingestion, and cleanup scheduling. It can contain user context, so privacy rules apply even when pages discuss it structurally.

The module has service tests and a PostgreSQL/pgvector integration test path. Database-specific behavior should use Testcontainers rather than H2.

## Evidence

- `src/main/java/com/fit/fitnessapp/memory/application/service/MemoryService.java`
- `src/main/java/com/fit/fitnessapp/memory/application/service/MemoryEventListener.java`
- `src/main/java/com/fit/fitnessapp/memory/application/service/MemoryCleanupService.java`
- `src/main/java/com/fit/fitnessapp/memory/application/service/MemoryCleanupScheduler.java`
- `src/main/java/com/fit/fitnessapp/memory/infrastructure/config/MemoryConfig.java`
- `src/test/java/com/fit/fitnessapp/memory/MemoryServiceTest.java`
- `src/test/java/com/fit/fitnessapp/memory/MemoryCleanupServiceTest.java`
- `src/test/java/com/fit/fitnessapp/memory/MemoryPgVectorIntegrationTest.java`
- [[testing-strategy]]
- [[security-and-privacy]]

## Contradictions

- Backlog items around pgvector and memory cleanup are resolved locally, but future changes still need integration-test evidence because pgvector behavior is database-specific.

## Open Questions

- Which user events should become durable memory, and which should stay transient?
- What retention policy should apply to memories without explicit expiration metadata?

## Next Actions

- Use PostgreSQL/Testcontainers for pgvector, Flyway, SQL, and JPA behavior.
- Keep memory examples structural and avoid private user content.
- Re-run integration profile for memory schema changes.
