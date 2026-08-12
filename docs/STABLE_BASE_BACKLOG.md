# STABLE BASE BACKLOG

Этот документ фиксирует завершённый исторический scope Stable Base перед Stage 2.

Он остаётся источником статуса для исходных задач `FND-001`–`FND-037`, но не для одноимённых решений нового product review. Канонический текущий порядок находится в `docs/superpowers/specs/2026-08-12-stage-2-roadmap-design.md`. Phase 1 нельзя начинать до выполнения нового Phase 0 exit gate.

---

## Status Overview

| Group | Status | Details |
|---|---|---|
| **P0: Data, Security, Reliability** | ✅ COMPLETED | All P0 acceptance criteria are implemented and covered by focused unit/PostgreSQL tests; final three-gate verification remains part of the exit gate |
| **P1: Reliability & Domain** | ✅ COMPLETED | P1 acceptance criteria are implemented and covered by unit, web, and PostgreSQL workflow tests; vector strategy remains an explicit capacity decision |
| **P2: Post-Stabilization Operations** | ✅ COMPLETED | Infrastructure/application boundaries, exact-scan capacity evidence, operations, observability and product-quality evaluation are covered by tests and runbooks; ANN indexing remains a future capacity decision |

---

## P0: Сначала это (Необратимые риски и фундамент)

| ID | Задача | Статус |
|---|---|---|
| `FND-001` | Исправить молчаливую потерю данных при разборе внешних ответов. | ✅ Completed (Typed outcomes `VALID`, `AUTHORITATIVE_EMPTY`, `PROVIDER_ERROR`, `MALFORMED`) |
| `FND-002` | Согласовать регистрацию пользователя и БД schema. | ✅ Completed (Flyway `V14`, `uq_users_email` unique constraint) |
| `FND-003` | Исправить ownership schema и добавить foreign keys. | ✅ Completed (Flyway `V14` ON DELETE CASCADE FKs across all child tables) |
| `FND-004` | Реализовать personal data lifecycle (GDPR export & deletion). | ✅ Completed (`UserDataLifecycleService`, `UserDataLifecycleController`, JSON payload exports, non-swallowing transactional cleanup) |
| `FND-005` | Согласовать dev runtime configuration. | ✅ Completed (`application-dev.properties` aligned) |
| `FND-006` | Создать durable job lifecycle core infrastructure. | ✅ Completed (Flyway `V16` & `V18`, `DurableJobService`, `DurableJobWorker`, `idempotency_key`, backoff retries, stuck job recovery, authorization ownership gates in `DurableJobController`) |
| `FND-007` | Убрать внешний I/O из DB-транзакций. | ✅ Completed (Removed `@Transactional` from Telegram handlers, `NutritionService` FatSecret sync, `WorkoutImportService` CSV parser, `DailyInsightService` AI provider calls) |
| `FND-008` | Сделать Telegram link workflow безопасным и продакшн-готовым. | ✅ Completed (Flyway `V15`, SHA-256 code hashing, 10m TTL, atomic `FOR UPDATE` consumption, `TelegramLinkController`) |
| `FND-009` | Ограничить публикацию данных Telegram только личными чатами. | ✅ Completed (`isUserChat()` enforcement across all handlers) |
| `FND-010` | Сделать Telegram delivery надёжным (outbox ledger). | ✅ Completed with at-least-once semantics (transactional enqueue, atomic `SKIP LOCKED` claim, `SENDING` recovery, bounded retries/backoff, correct 400/429/5xx classification, PostgreSQL concurrency test). Exactly-once delivery is not provided by Telegram API. |
| `FND-011` | Ввести AI safety и output validation. | ✅ Completed for current workflows (strict type/period/macro/score/list/length validation, boundary-tag escaping, daily/report/Telegram integration) |
| `FND-012` | Ограничить вызовы AI провайдеров и их стоимость. | ✅ Completed (single-response parsing, typed 400/401/429/timeout failures, configurable overall deadline and provider-attempt cap, global/per-user bulkheads, transactional PostgreSQL hourly token budgets, call-count and concurrency tests) |
| `FND-013` | Закрепить PostgreSQL integration test baseline. | ✅ Completed (`mvn verify -Pintegration` Testcontainers gate) |

---

## P1: Продакшн-надёжность и доменная корректность

| ID | Задача | Статус |
|---|---|---|
| `FND-014` | Единая time policy (UTC Clock, user IANA timezone, Instant). | ✅ Completed: UTC `Clock` is injected into time-sensitive services, user IANA timezone is persisted/validated through `UserTimeApi`, user-facing date workflows use the stored zone, naive note timestamps are normalized to `TIMESTAMPTZ`, and DST-overlap round-trip is covered in PostgreSQL |
| `FND-015` | Нормализовать reporting periods (`DateRange`). | ✅ Completed (Domain `DateRange` record supporting ISO week Monday-Sunday and calendar month bounds with leap year rules) |
| `FND-016` | Создать production scheduler model. | ✅ Completed: explicit UTC cron, configurable window/batch size, same-instance overlap guard, durable per-user idempotency and failure isolation are implemented and tested |
| `FND-017` | Исправить report coverage accuracy. | ✅ Completed (`NutritionDay` with `NutritionDataStatus` `PRESENT`, `MISSING_SYNC`, `NO_RECORDS`, `daysTracked` in `NutritionWeeklyStatsDto`) |
| `FND-018` | Исправить FatSecret profile sync `REQUIRES_NEW` self-invocation. | ✅ Completed: orchestration and provider calls run without a DB transaction; persistence ports retain short transactions; provider failures propagate to scheduler counters |
| `FND-019` | Ввести execution idempotency contract. | ✅ Completed: non-empty `idempotency_key` is mandatory, deduplication/claim are atomic, stuck jobs recover, and real nutrition/AI executors finish or fail jobs |
| `FND-020` | RFC-compliant CSV parsing & error recovery. | ✅ Completed: Jefit parser handles quoted delimiters, escaped quotes, multiline records, BOM and row-level warnings without aborting valid imports; adversarial tests added |
| `FND-021` | Workout import merge semantics. | ✅ Completed: re-import is idempotent, stale exercises/cardio are removed, repeated Jefit IDs are scoped per workout, duplicate sessions/dates are deduplicated, and merge tests cover conflict cases |
| `FND-022` | Domain invariants & database `CHECK` constraints. | ✅ Completed: Flyway `V25` adds non-blank, positive/non-negative, enum and lifecycle checks; PostgreSQL rejection tests cover representative violations |
| `FND-023` | Notes/weight event lifecycle & response accuracy. | ✅ Completed: asynchronous confirmations are honest, note/weight listeners persist through Modulith, note-memory deletion is event-driven, and PostgreSQL verifies listener completion |
| `FND-024` | Telegram command parser & private-chat security. | ✅ Completed: all handlers enforce private linked chats and exact command-token parsing; prefix/argument edge cases have regression coverage |
| `FND-025` | Auth/session contract & security error handling. | ✅ Completed: stateless JWT policy, public auth routes, role gates, invalid/expired token errors, CORS and unauthenticated/forbidden MockMvc coverage are verified |
| `FND-026` | Unified API errors & HTTP semantics. | ✅ Completed: validation/auth/domain/not-found paths share timestamped `ApiError`; report batch endpoints return explicit JSON status/period bodies and external provider details are not exposed |
| `FND-027` | AI freshness & memory provenance. | ✅ Completed for current workflows: snapshot hashes and versioned prompts prevent stale regeneration; insight and note memories carry source metadata/stable IDs and deletion events remove derived memory |
| `FND-028` | Observability & logging hygiene. | ✅ Completed for the stable base: privacy-safe structured logs, aggregate Actuator gauges for jobs/outbox/publications, alert thresholds and operator procedures are documented without user-data tags |
| `FND-029` | Modulith event publication management. | ✅ Completed: JDBC publications are persisted, restart replay is enabled, listener completion is asserted against PostgreSQL, and the operations runbook documents retention/recovery |
| `FND-030` | Spring Boot & Spring AI stack alignment. | ✅ Completed: dependency versions are pinned to the supported Boot/AI/Modulith lines and `mvn dependency:analyze` is green |
| `FND-031` | CI & static analysis hygiene. | ✅ Completed: CI runs unit, architecture, PostgreSQL integration, dependency analysis and privacy-sensitive logging checks as required gates |
| `FND-032` | Documentation as source of truth. | ✅ Completed: backlog, ADRs 0010–0013, vector benchmark note, operations runbook and test gate commands are reconciled |

---

## P2: Post-Stabilization Operations

| ID | Задача | Статус |
|---|---|---|
| `FND-033` | Modulith public contracts & boundary encapsulation. | ✅ Completed: Modulith verification and CodeHygiene checks enforce no cycles, no cross-module persistence imports and stable public API usage |
| `FND-034` | Infrastructure/Application layer separation. | ✅ Completed: weekly and monthly report workflows are separate application services, `FitnessAiService` is an event/job façade, and report/context reads use the `AiInsightPort` contract; characterization tests remain green |
| `FND-035` | Vector search capacity benchmark. | ✅ Completed for the exact-scan baseline: PostgreSQL harness fixes 512 memories/user and a 1,000 ms p95 SLO; HNSW incompatibility with `vector(2048)` is proven, so runtime intentionally remains `index-type=NONE` until a future lower-dimension/`halfvec`/IVFFlat benchmark |
| `FND-036` | Production readiness & graceful execution. | ✅ Completed for application-managed work: durable workers, health endpoints, clean-profile smoke coverage, bounded AI shutdown and Spring scheduler/async termination settings are covered by tests/runbook; external provider shutdown remains bounded by the AI execution deadline |
| `FND-037` | Product-quality evaluation & regression safety. | ✅ Completed: formal evaluation scope/evidence is recorded in `docs/evaluations/stable-base-product-quality.md`, with PostgreSQL end-to-end workflow regression coverage |

---

## Stable Base Exit Gate Status

- ✅ `mvn test` -> **BUILD SUCCESS** (283 tests, 0 failures/errors)
- ✅ `mvn test -Parchitecture` -> **BUILD SUCCESS** (0 failures/errors; 1 documented skip)
- ✅ `mvn verify -Pintegration` -> **BUILD SUCCESS** (31 PostgreSQL/Testcontainers tests, 0 failures/errors)
- ✅ Default production-like and `dev` profiles start against a clean PostgreSQL baseline (`DevProfileSmokeIntegrationTest`).
- ✅ FatSecret, AI, nutrition/workout event publication, and Telegram delivery transaction boundaries have PostgreSQL regression tests; provider calls are outside DB transactions and Telegram insight notifications enqueue before network delivery.
- ✅ New durable jobs require `idempotency_key` and have status, bounded retries, stuck-job recovery and operator retry.
- ✅ Ownership checks and PostgreSQL-backed GDPR export/deletion are covered.
- ✅ Telegram responses no longer claim persistence before asynchronous work completes.
- ✅ End-to-end `sync/import -> durable insight request -> memory -> delivery` is verified by `StableBaseWorkflowIntegrationTest` with provider boundaries mocked.
- ✅ End-to-end `Telegram command -> transactional event -> durable save -> confirmation` is verified by `StableBaseWorkflowIntegrationTest`.
