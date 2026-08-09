# P0 Phase 1: Data Foundation Design

## Scope

This phase implements `FND-001`, `FND-002`, `FND-003`, `FND-005`, and
`FND-013` from `docs/STABLE_BASE_BACKLOG.md`. It establishes a trusted
PostgreSQL baseline before later P0 work changes lifecycle, jobs, Telegram,
or AI behavior.

The phase is complete only when all acceptance criteria below are covered and
the three project gates pass:

- `mvn test`
- `mvn test -Parchitecture`
- `mvn verify -Pintegration`

Existing Flyway migrations are immutable. Database changes use new,
forward-only migrations.

## FND-001: Authoritative Nutrition Synchronization

### Contract

The monthly FatSecret boundary returns a typed result with exactly four
outcomes:

- `VALID`: a structurally valid, complete monthly snapshot containing one or
  more day summaries.
- `AUTHORITATIVE_EMPTY`: a structurally valid FatSecret response that
  explicitly represents a month with no day summaries.
- `PROVIDER_ERROR`: a successful HTTP response whose payload contains a
  FatSecret error object.
- `MALFORMED`: a payload that is invalid JSON, lacks the expected month
  structure, has a day node of the wrong type, or contains invalid required
  day fields.

HTTP failures continue to raise `ExternalApiException`; they are equivalent
to `PROVIDER_ERROR` for the no-write rule.

`FatSecretApiPort` exposes the typed monthly result. Provider-specific JSON
classification stays in `FatSecretApiAdapter`; `NutritionService` consumes
only the domain outcome and does not inspect JSON or provider error codes.

### Write and event rules

For `VALID`, the service saves the returned monthly summaries, deletes local
days missing from the complete snapshot, performs the existing recent-day
detail synchronization, and publishes events for actual changes.

For `AUTHORITATIVE_EMPTY`, the service may delete all local days in the
requested month because absence is explicit and authoritative. It publishes
events for the deletions, using the existing changed-date behavior.

For `PROVIDER_ERROR`, `MALFORMED`, or an HTTP/transport failure, the service
performs no nutrition writes and publishes no nutrition events. It propagates
a typed application-visible failure rather than reporting successful sync.

Fixture tests cover array, singleton, explicit empty, provider error,
malformed JSON, missing month structure, wrong day-node type, and invalid
required fields. Service tests verify both positive writes and the no-write,
no-event invariant.

## FND-002: Registration Identity Contract

### Canonical representation

Usernames and email addresses are trimmed before validation and persistence.
Usernames retain their original case and remain case-sensitive. Email
addresses are lower-cased with `Locale.ROOT` and are case-insensitive by
canonical storage.

The canonical limits are:

- username: 1 to 64 characters after trimming;
- email: at most 255 characters after trimming and lower-casing;
- password: existing validation rules remain unchanged.

The request DTO, JPA entity metadata, and database columns use the same
username and email limits. Normalization happens once at the registration
application boundary so pre-checks and persisted values are identical.

### Uniqueness and races

The database enforces uniqueness for both username and canonical email. The
existing pre-checks remain only for a friendly fast failure; correctness
comes from database constraints.

A new Flyway migration audits existing normalized email collisions before
adding the unique constraint. It widens username to 64 and email to 255 and
normalizes existing email values. If collision-free migration cannot be
proved, Flyway fails rather than silently selecting or deleting an account.

`DataIntegrityViolationException` raised by a concurrent duplicate insert is
translated to `UserAlreadyExistsException`, which the existing HTTP exception
mapping must return as `409 Conflict`. Unit/MockMvc tests cover normalization,
limits, and the exception mapping. A PostgreSQL integration test uses two
concurrent registrations with the same canonical email and proves that one
succeeds, one returns the duplicate outcome, and only one user row exists.

## FND-003: Ownership Schema

A new migration establishes ownership for `fatsecret_day`, `workout`, and
`workout_cardio`:

- audit for null or orphan `user_id` values and fail the migration if any are
  present;
- make each ownership column `NOT NULL`;
- add foreign keys to `users(id)`;
- use `ON DELETE CASCADE` so account deletion removes the owned aggregate;
- make aggregate child foreign keys non-null where the domain requires a
  parent, and use cascade deletion for those children.

The migration uses explicit, stable constraint names and guards against
constraints that already exist where necessary. It never deletes or repairs
orphan rows automatically.

PostgreSQL integration tests prove that an unknown owner is rejected, null
owners are rejected, and deleting a user removes the complete nutrition and
workout aggregates without leaving children.

## FND-005: Consistent pgvector Runtime

All profiles use the Flyway-managed `user_memory` table and the same vector
dimension. The dev profile must not refer to `vector_store` and must not
request an HNSW index for `vector(2048)`. Exact scan remains the intentional
strategy established by the existing migrations and `MemoryConfig`.

A dev-profile PostgreSQL smoke test starts the Spring context against a clean
pgvector database, lets Flyway create the schema, and verifies that
`user_memory.embedding` is `vector(2048)` and no HNSW index exists for that
column. Provider clients are disabled or mocked; the smoke test performs no
external network calls.

## FND-013: PostgreSQL Release Gate

The existing Maven `integration` profile and CI job remain the single
PostgreSQL release gate. Tests added by this phase use the shared
Testcontainers PostgreSQL/pgvector support and the `*IntegrationTest` naming
convention so `mvn verify -Pintegration` executes them.

The integration suite must cover:

- clean Flyway startup;
- registration column widths, canonical email uniqueness, and concurrent
  duplicate registration;
- ownership foreign keys, non-null constraints, and cascades;
- dev-profile `user_memory` startup and exact-scan configuration;
- existing PostgreSQL persistence behavior.

CI continues to run `mvn verify -Pintegration` as a blocking job. No H2 test
is accepted as evidence for these requirements.

## Boundaries and Risks

This phase does not implement account deletion APIs, Telegram identity,
durable jobs, provider budgets, or AI safety; those remain later P0 phases.
It does provide the database ownership and integration-test foundation those
phases require.

The main migration risk is pre-existing production data that violates the new
constraints. The migration deliberately fails with an actionable error; data
remediation is an operator action and is not automated by application code.

The main synchronization risk is confusing a missing JSON path with an empty
snapshot. The typed parser contract makes the distinction explicit and tests
every accepted response shape before destructive reconciliation is allowed.
