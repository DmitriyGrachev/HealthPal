# Phase 0 Truth and Recovery — implementation plan

> Execute under the authority of the [`Stage 2 canonical roadmap`](../specs/2026-08-12-stage-2-roadmap-design.md). Each numbered iteration uses RED → GREEN → review → verification → one commit. Existing Flyway migrations V1–V28 remain immutable.

**Goal:** prove durable intent, fenced recovery, historical upgrades, provider retention, lifecycle export, and a supported Java/Spring platform before product state is added.

## Fixed external-policy decisions

### FatSecret

The conservative default is identifier-only/re-fetch. Until an account-specific written agreement explicitly permits broader retention, FitnessApp may retain indefinitely only the exact public whitelist: `auth_secret`, `auth_token`, `exercise_id`, `food_category_id`, `food_entry_id`, `food_id`, `recipe_id`, `recipe_types`, `saved_meal_id`, `saved_meal_item_id`, and `serving_id`.

All other FatSecret response fields and their copied, aggregated, hashed, embedded, or AI-derived representations are restricted provider content. The default implementation does not persist that content at all. If an immediate-response cache is necessary, it is in-memory only with a maximum 23-hour TTL so process shutdown removes it and no database outage can extend retention. Manual FitnessApp records remain user-owned only when provenance proves they were not copied from FatSecret.

Official evidence:

- <https://platform.fatsecret.com/docs/guides/storable-data>
- <https://platform.fatsecret.com/terms>
- <https://platform.fatsecret.com/docs/v2/food_entries.get>
- <https://platform.fatsecret.com/docs/v2/food_entries.get_month>
- <https://platform.fatsecret.com/docs/v2/weights.get_month>
- <https://platform.fatsecret.com/docs/v5/food.get>

Premier/Enterprise “Caching” is not treated as blanket permission. Broader retention requires written terms covering fields, duration, derived data, deletion/export, backups, attribution, and termination.

### Spring platform

The final baseline is Java 21, Spring Boot 4.1.0, Spring AI 2.0.0, and Spring Modulith 2.1.0. This is supported by official pairwise compatibility and released POM evidence; Spring does not publish a single three-project matrix for this exact combination.

Upgrade through the official Boot-recommended latest 3.5 line first:

```text
bridge: Java 21 + Boot 3.5.16 + AI 1.1.8 + Modulith 1.4.12
target: Java 21 + Boot 4.1.0 + AI 2.0.0 + Modulith 2.1.0
```

The bridge is a migration checkpoint, not the supported final release.

Official evidence:

- <https://docs.spring.io/spring-boot/system-requirements.html>
- <https://docs.spring.io/spring-ai/reference/getting-started.html>
- <https://github.com/spring-projects/spring-ai#spring-boot-version-compatibility>
- <https://central.sonatype.com/artifact/org.springframework.modulith/spring-modulith-starter-core>
- <https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide>
- <https://docs.spring.io/spring-ai/reference/upgrade-notes.html>

## Iteration 0.2 — Historical upgrade bridges and fixture harness

This iteration comes before new constraints so every later migration is tested against dirty state.

### RED

Add `src/test/java/com/fit/fitnessapp/infrastructure/persistence/HistoricalUpgradeIntegrationTest.java` using PostgreSQL Testcontainers and isolated schemas.

Create fixtures and prove failure without repair for:

1. V20 durable rows with null idempotency keys plus a deliberate existing `legacy:<id>` collision before V21.
2. V22 naive timestamps across DST before V23; document and assert the chosen UTC interpretation.
3. V24 invalid statuses, negative/exhausted attempts, blank Telegram text, invalid measurements, and invalid notes before V25.
4. V25 memory metadata containing missing, nonnumeric, orphaned, negative, and oversized numeric `user_id` before V26.
5. V19 legacy `SENDING` with `claimed_at IS NULL`, old `RUNNING`, and exhausted pending work.
6. Incomplete Modulith publications through V27 ownership backfill.
7. A test-only transactional migration that changes data, performs invalid DDL, rolls back completely, then succeeds on corrected rerun.

Run the narrow RED gate:

```powershell
mvn -Dit.test=HistoricalUpgradeIntegrationTest verify -Pintegration
```

### GREEN

Add versioned idempotent operational scripts, outside Flyway's normal location so they execute only at the documented version boundary:

- `src/main/resources/db/upgrade/pre-v21-durable-job-idempotency.sql`;
- `src/main/resources/db/upgrade/pre-v23-absolute-timestamps.sql`;
- `src/main/resources/db/upgrade/pre-v25-domain-invariants.sql`;
- `src/main/resources/db/upgrade/pre-v26-memory-owner.sql`.

The integration test migrates to the preceding target, inserts dirty fixtures, executes the matching bridge in a transaction, then migrates to latest. Scripts use deterministic replacement keys, range-safe parsing before any cast, staged cleanup, and repeatable predicates. They never log or export row content.

Update:

- `docs/runbooks/stable-base-operations.md` with exact version checkpoints, preflight counts, transaction/rollback, and backup requirements;
- `docs/adr/0004-keep-flyway-migrations-forward-only.md` to document bridge scripts as the supported repair for a database blocked before a historical migration.

### Verify and commit

```powershell
mvn -Dit.test=HistoricalUpgradeIntegrationTest,DurableOwnershipMigrationIntegrationTest verify -Pintegration
git diff --check
```

Commit: `test: prove historical database upgrade bridges`

## Iteration 0.3 — Versioned module-owned personal-data export

### RED

Add:

- `auth/application/service/UserDataExportManifestServiceTest.java`;
- `auth/adapter/in/web/UserDataExportV2ControllerTest.java`;
- integration coverage asserting deterministic module ordering, version/disclosure metadata, no OAuth secret export, and no cross-user fragments.

Keep the existing `/api/v1/user/me/export` behavior unchanged.

### GREEN

Add:

- `auth/domain/UserDataExportManifest.java`;
- `auth/domain/UserDataModuleExport.java`;
- `api/lifecycle/DataRetentionDisclosure.java` (neutral lifecycle contract; it must not depend on `auth`);
- `auth/application/port/in/UserDataExportManifestUseCase.java`;
- `auth/application/service/UserDataExportManifestService.java`;
- `auth/adapter/in/web/UserDataExportV2Controller.java` at `GET /api/v2/user/me/export`;
- move the neutral lifecycle SPI records from the broad root to `api/lifecycle/` and add `api/lifecycle/package-info.java` with `@NamedInterface("lifecycle")`;
- update every existing participant import to depend on `api::lifecycle`, not broad `api`;
- replace the obsolete `allowedDependencies` update instruction with a focused architecture assertion that the named `api::lifecycle` interface exists and contains the lifecycle contracts; do not add broad allow-lists in this iteration.

Extend `UserDataLifecycleParticipant` with default `exportSchemaVersion()` and `retentionDisclosure()` methods. V2 assembles ordered module fragments generically; `auth` no longer enumerates fields for future modules. V1 continues using `UserDataExportDto` for compatibility.

Disclosure distinguishes local canonical data, expiring provider cache, external transfer, backup limitations, and deletion scope without claiming impossible remote erasure. The V2 controller depends on `UserDataExportManifestUseCase`, never on the concrete service.

### Verify and commit

```powershell
mvn -Dtest=UserDataExportManifestServiceTest,UserDataExportV2ControllerTest test
mvn -Dit.test=UserDataLifecycleServiceIntegrationTest verify -Pintegration
mvn test -Parchitecture
```

Commit: `feat: add versioned module owned data export`

## Iteration 0.4 — Durable-job lease fencing

### RED

Update unit tests and add `DurableJobLeaseConcurrencyIntegrationTest` for:

- claimant A generation 1 expires, B claims generation 2, then A cannot complete/fail/skip or heartbeat B's claim;
- final-attempt crash recovers to terminal `FAILED`, never exhausted `PENDING`;
- heartbeat prevents recovery;
- concurrent recovery/reclaim produces exactly one newer generation;
- manual retry rejects `RUNNING` and `SUCCEEDED` and accepts only terminal retryable states;
- a rejected fenced mutation returns false and emits a stable non-PII reason code;
- `NutritionSyncScheduler` and `DurableJobWorker` execute the refreshed claimed DTO, not a pre-claim snapshot.

### GREEN

Add `src/main/resources/db/migration/V29__add_durable_job_leases.sql`:

- `lease_generation BIGINT NOT NULL DEFAULT 0`;
- `lease_owner VARCHAR(128)`;
- `lease_expires_at TIMESTAMPTZ`;
- `claimed_at TIMESTAMPTZ`;
- normalization of current `RUNNING` rows;
- current-state indexes;
- staged/validated constraints: `RUNNING` requires owner/expiry, non-running has no active lease, exhausted pending is prohibited.

Add:

- `job/DurableJobClaim.java` containing the refreshed `DurableJobDto`, owner, generation, and expiry;
- `job/JobFailure.java` with stable code and safe detail;
- `job/application/service/DurableJobLeaseHeartbeat.java`.

Replace boolean `startJob`/ID-only mutations in `DurableJobUseCase` with:

```java
Optional<DurableJobClaim> claimJob(Long jobId, String owner, Duration lease);
Optional<DurableJobClaim> claimNext(String owner, Duration lease);
boolean heartbeat(DurableJobClaim claim, Duration extension);
boolean completeJob(DurableJobClaim claim);
boolean failJob(DurableJobClaim claim, JobFailure failure);
boolean skipJob(DurableJobClaim claim, String reasonCode);
```

Every active mutation includes `id`, `status='RUNNING'`, `lease_owner`, and `lease_generation` in one SQL statement. Recovery uses PostgreSQL `NOW()` and atomically splits expired work:

- attempts below max → `PENDING`;
- attempts at max → `FAILED` with `CLAIM_TIMEOUT_MAX_ATTEMPTS`.

Retry is allowed only from `FAILED` or `SKIPPED`, clears lease state, resets attempts, and rejects `RUNNING`/`SUCCEEDED` through a stable conflict response.

Update `DurableJobWorker`, `NutritionSyncScheduler`, `NutritionSyncJobExecutor` tests, controller tests, and operations runbook. Provider-specific idempotency keys remain mandatory where providers support them; lease fencing protects the ledger but does not claim exactly-once external side effects.

### Verify and commit

```powershell
mvn -Dtest=DurableJobServiceTest,DurableJobWorkerTest,NutritionSyncSchedulerTest test
mvn -Dit.test=DurableJobLeaseConcurrencyIntegrationTest,DurableJobServiceIntegrationTest verify -Pintegration
mvn test -Parchitecture
```

Commit: `fix: fence durable job leases`

## Iteration 0.5 — Telegram delivery lease fencing

### RED

Extend `TelegramOutboxConcurrencyIntegrationTest` and retention tests:

- A's lease expires and recovery terminalizes it; A's late success, retryable failure, permanent failure, and revocation result cannot mutate the terminal row;
- crash after Telegram accepts attempt 1 but before local completion becomes owned `DELIVERY_UNKNOWN` and is never automatically resent;
- every expired owned `SENDING` lease becomes `DELIVERY_UNKNOWN` regardless of attempt number because provider acceptance is unknowable;
- anonymous exhausted or uncertain content is deleted;
- legacy `SENDING/claimed_at=NULL` and owned exhausted `PENDING` are normalized;
- claim occurs one row immediately before the provider call, so a batch cannot age while queued;
- unlink/account deletion during a claim removes owned rows and stale completion is a no-op;
- provider send runs outside a transaction;
- exactly one active claimant exists under concurrent workers.

### GREEN

Add `src/main/resources/db/migration/V30__add_telegram_outbox_leases.sql`:

- `lease_generation`, `lease_owner`, `lease_expires_at`;
- `DELIVERY_UNKNOWN` as an explicit terminal status;
- normalization of null-claimed and exhausted legacy rows;
- owner/current-state indexes;
- constraints coupling `SENDING` to a complete lease and prohibiting exhausted `PENDING`.

Add `TelegramDeliveryClaim` and a stable worker ID. Replace the 50-row claim with `claimNextDelivery`: one atomic `FOR UPDATE SKIP LOCKED` claim immediately before send. Guard authorization failure, success, retry, permanent failure, and timeout by ID + owner + generation.

Telegram provider timeout must be strictly less than the lease duration. Recovery never reclaims an expired `SENDING` delivery: because Telegram has no request idempotency/ack lookup, every expired owned claim may already have been accepted and therefore becomes `DELIVERY_UNKNOWN`; anonymous uncertain content is deleted. Automatic retry is permitted only when the current live claimant receives a failure classified as provably pre-send/not-accepted. Ambiguous network, timeout, and server outcomes become `DELIVERY_UNKNOWN` immediately. Retrying an uncertain owned delivery requires an explicit new user/operator intent that creates a new outbox row and preserves the uncertainty audit; it cannot reset the old row. Owned audit rows remain owner-bound and cascade on unlink/delete.

### Verify and commit

```powershell
mvn -Dtest=TelegramBotServiceTest test
mvn -Dit.test=TelegramOutboxConcurrencyIntegrationTest,TelegramOutboxRetentionIntegrationTest verify -Pintegration
mvn test -Parchitecture
```

Commit: `fix: fence telegram delivery leases`

## Iteration 0.6 — Atomic source state and durable intent

### RED

Add:

- `nutrition/NutritionAtomicPublicationIntegrationTest.java`;
- `workout/WorkoutAtomicPublicationIntegrationTest.java`;
- unit tests for duplicate, stale-version, missing-source, and replay-after-delete convergence.

Fault injection cases:

1. failure after source write but before publish rolls back both source and publication;
2. successful commit leaves source and incomplete Modulith publication atomically;
3. process stops before listener dispatch, restart/republication delivers intent;
4. duplicate event is idempotent by event/source version;
5. older event cannot overwrite a newer projection;
6. deleted source/account replay converges to absence.

### GREEN

Add neutral event metadata in the stable API package:

```java
record DomainEventMetadata(
    UUID eventId,
    Long userId,
    String sourceType,
    String sourceId,
    long sourceVersion,
    ChangeType changeType,
    String contentHash,
    UUID lifecycleEpoch,
    int schemaVersion,
    Instant occurredAt
) {}
```

Add short transaction coordinators:

- `nutrition/application/service/NutritionSyncCommitService.java`;
- `workout/application/service/WorkoutImportCommitService.java`;
- corresponding `CommitResult` records.

Network fetch and CSV parsing stay outside transactions. Inside one `@Transactional` boundary, save canonical state and call normal `ApplicationEventPublisher.publishEvent`; Spring Modulith records durable publication in that transaction. The coordinators inject `ApplicationEventPublisher` directly. Keep the existing `TransactionalEventPublisher` temporarily for the Telegram note/weight callers that have not yet migrated; nutrition and workout must no longer use it. Removing or changing that legacy wrapper is a later atomic-source iteration, not a prerequisite for this commit.

Use one source per user and calendar date:

- nutrition source type `NUTRITION_DAY`, source ID ISO date;
- workout source type `WORKOUT_DAY`, source ID ISO date.

A monthly nutrition commit or multi-date workout import publishes one metadata-bearing identifiers/hash-only event per changed or deleted date, all inside the same transaction as the corresponding canonical writes. Provider values, macros, exercise text, and other raw content never enter the event. Existing top-level `userId` remains for durable publication ownership; the same value is present in metadata and must match.

Keep the existing event FQCNs and their legacy JSON fields deserializable because incomplete Spring Modulith rows persist the class name and serialized JSON. Add metadata without renaming/removing old record components. New constructors populate legacy numeric/count fields only with neutral defaults and hashes/identifiers; consumers ignore them and require complete metadata. Old rows with absent metadata deserialize successfully and converge as a no-op. V31 removes pre-metadata nutrition/workout publication rows after source-state backfill so legacy raw macros/counts are not retained in the global publication ledger.

Add `src/main/resources/db/migration/V31__version_domain_sources.sql`:

- add a non-null UUID `lifecycle_epoch` to `users`, generated once per account lifetime;
- add module-owned `nutrition_source_state` and `workout_source_state` tables keyed by `(user_id, source_date)` with monotonic positive `source_version`, current `content_hash`, `present` tombstone state, lifecycle epoch, timestamps, owner FK/cascade, epoch consistency, and shape constraints;
- backfill one current version for every existing nutrition/workout date without exposing raw content;
- delete legacy `NutritionSyncedEvent` / `WorkoutImportedEvent` publication rows that have no complete metadata after the backfill;
- keep tombstones across source delete/recreate while the owning account exists;
- extend nutrition/workout lifecycle export and deletion coverage for the new rows.

Consumers reject legacy events without complete metadata, lifecycle-epoch mismatches, future versions, and missing owners as successful no-ops. They reread the current source state before enqueueing work. Duplicate delivery is idempotent by stable source identity, event ID, and source version. A stale event may only converge from current source truth; it cannot serialize or restore the old payload. Before an AI projection is committed, the source versions/hash used by its snapshot are revalidated inside the projection transaction so older work cannot overwrite a newer or deleted source. Current tombstones converge the projection to the result implied by all remaining sources.

Consumers reread the exact current source version through module-owned ports. Missing source/owner is a successful no-op; stale version deletes or preserves the newer projection according to source truth. Account deletion cascades source state and outstanding publications, and a removed lifecycle epoch can never be recreated from replay.

Completed publication rows contain only identifiers, hashes, versions, and timing metadata. Export only safe receipt columns, disclose account-lifetime retention, and continue deleting rows through the enforced owner FK; never export or log serialized raw event content.

Record the design in `docs/adr/0016-atomic-state-and-durable-intent.md`.

### Verify and commit

```powershell
mvn -Dtest='*Event*Test,*Replay*Test' test
mvn -Dit.test=NutritionAtomicPublicationIntegrationTest,WorkoutAtomicPublicationIntegrationTest,UserDataLifecycleServiceIntegrationTest,HistoricalUpgradeIntegrationTest verify -Pintegration
mvn test -Parchitecture
```

Commit: `fix: commit source state and durable intent atomically`

## Iteration 0.7 — FatSecret provenance and restricted-data non-retention

This iteration may delete legacy/restricted provider-derived rows. Before applying it outside disposable environments, follow the runbook backup and preflight-count procedure. Deleted restricted data is intentionally not recoverable through the application.

### RED

Add provider-mocked unit and PostgreSQL tests proving:

- imported food names/macros/day totals/FatSecret weights are never written to durable storage;
- any immediate-response cache is in-memory only, has a maximum 23-hour TTL, and disappears on restart;
- permitted identifiers remain separately available;
- manual weight and independently user-authored data do not expire;
- hashes, AI context, insights, memory projections, and Experiment evidence cannot outlive their restricted FatSecret source;
- export omits restricted provider content and discloses the policy;
- disconnect cancels sync jobs, removes credentials, restricted rows/caches/projections, and cannot be undone by replay;
- account delete and provider termination remove all local provider data;
- real provider APIs are never called in tests.

### GREEN

Add `docs/adr/0015-fatsecret-import-and-storage-policy.md` with the fixed policy and official links.

Add `src/main/resources/db/migration/V32__enforce_fatsecret_data_retention.sql`:

- `fatsecret_provider_identifiers` for only allowed identifier fields, owner FK, and receipt timestamps;
- delete existing `fatsecret_day` and `fatsecret_food` restricted content after the documented preflight/backup step;
- delete existing `weight_history.weight_source='FATSECRET'` while leaving `MANUAL` untouched;
- remove derived nutrition sync hashes that encode restricted content;
- owner, identifier-shape, and provider-termination constraints for the allowed identifier table.

Add:

- `NutritionDataOrigin` and `ProviderDataRetentionPolicy`;
- an optional Caffeine cache configured with a hard maximum 23-hour TTL and no persistence;
- provider adapters that map restricted fields only into short-lived response DTOs;
- disconnect cleanup through `NutritionUserDataLifecycleParticipant`;
- projection/source invalidation events containing IDs only.

Disable historical FatSecret sync as a canonical source. Provider reads are ephemeral or form the short-lived in-memory cache; no scheduled job writes restricted fields. Experiments cannot use restricted provider evidence. Manual records require an explicit manual path and provenance.

Do not claim remote profile deletion: call only documented resource DELETE operations where applicable and disclose uncovered provider-side scope.

### Verify and commit

```powershell
mvn -Dtest='*FatSecret*Retention*Test,*FatSecret*Test' test
mvn -Dit.test=FatSecretRetentionIntegrationTest,UserDataLifecycleServiceIntegrationTest verify -Pintegration
mvn test -Parchitecture
powershell -File .codex/hooks/privacy-scan.ps1
```

Commit: `fix: enforce fatsecret provider data retention`

## Iteration 0.8 — Current-state migration normalization

### RED/GREEN

Extend `HistoricalUpgradeIntegrationTest` through V32 and add current-version fixtures for:

- legacy job/outbox lease state;
- terminal owned/anonymous Telegram retention;
- malformed memory metadata and owner cascade;
- incomplete publications;
- permitted FatSecret identifiers and absence of restricted durable content;
- repeat application of every bridge script;
- clean-schema migration and every supported historical checkpoint to latest.

Fix only with append-only migrations or the already documented pre-version bridge. Do not edit V1–V30. If a new defect is found after V32 has been committed, add V33 and shift later reserved migration numbers before their first commit.

### Verify and commit

```powershell
mvn -Dit.test=HistoricalUpgradeIntegrationTest,DurableOwnershipMigrationIntegrationTest verify -Pintegration
```

Commit: `test: complete dirty upgrade coverage`

## Iteration 0.9 — Spring 3.5 compatibility checkpoint

### RED/GREEN

In one isolated commit update `pom.xml` to Boot 3.5.16, Spring AI 1.1.8, and Spring Modulith 1.4.12. Resolve compilation/configuration changes without product behavior changes. Inspect the effective POM and dependency graph for mixed trains. Update test fixtures for changed Modulith publication schema/behavior and prove current migrations against it.

Run:

```powershell
mvn dependency:tree "-Dincludes=org.springframework.boot:*"
mvn dependency:tree "-Dincludes=org.springframework.ai:*"
mvn dependency:tree "-Dincludes=org.springframework.modulith:*"
mvn dependency:analyze
mvn test
mvn test -Parchitecture
mvn verify -Pintegration
```

Commit: `chore: validate spring 3.5 migration checkpoint`

## Iteration 0.10 — Supported Boot 4.1 platform baseline

### RED/GREEN

Update `pom.xml` to Boot 4.1.0, Spring AI 2.0.0, and Spring Modulith 2.1.0.

Required adaptations:

- focused Boot 4 web, Flyway, and test starters;
- Jackson 3 application imports/configuration and deliberate isolation for any third-party Jackson 2 boundary;
- `@MockitoBean`/`@MockitoSpyBean` and explicit MockMvc auto-configuration;
- Spring AI 2 artifact/property changes, including pgvector starter and embedding model property;
- explicit intended chat temperature;
- Modulith 2.1 publication states, resubmission, schema upgrade, and replay tests;
- Jakarta EE 11 / Servlet 6.1 compatibility for security, springdoc, and Telegram adapters;
- removal of obsolete broad `dependency:analyze` suppressions and addition of actual direct dependencies where code imports them.

Use `mvn help:effective-pom -Doutput=target/effective-pom.xml` and dependency trees to prove there is no accidental Boot 3/Spring Framework 6 mixture.

### Verify and commit

```powershell
mvn dependency:analyze
mvn test
mvn test -Parchitecture
mvn verify -Pintegration
powershell -File .codex/hooks/privacy-scan.ps1
npx graphify hook-rebuild
git diff --check
```

Commit: `chore: move to supported spring platform baseline`

## Iteration 0.11 — Phase 0 final gate and documentation reconciliation

Update:

- `TESTING.md` to describe the real integration/architecture gates rather than future coverage;
- `docs/STABLE_BASE_BACKLOG.md` to distinguish completed Stable Base scope from Stage 2 Phase 0;
- `docs/superpowers/specs/2026-08-09-fitnessapp-product-evolution-review.md` decision/status register;
- `docs/runbooks/stable-base-operations.md` for lease generations, terminal/uncertain recovery, upgrade bridges, FatSecret retention, and platform versions;
- ADR `0010` so it no longer overstates generationless recovery.

Run the full gate from a clean working tree except the intended doc changes:

```powershell
mvn test
mvn verify -Pintegration
mvn test -Parchitecture
mvn dependency:analyze
powershell -File .codex/hooks/privacy-scan.ps1
npx graphify hook-rebuild
git diff --check
```

Request independent review of the complete Phase 0 range. Resolve all Critical/Important findings and rerun affected gates.

Commit: `docs: close truth and recovery phase gate`
