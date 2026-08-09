# STABLE BASE BACKLOG

Этот документ определяет единый список задач и порядок стабилизации `FitnessApp` перед созданием продуктов на его основе.

Использовать как единственную правду для фундаментальных исправлений. Не начинать продуктовые фичи до выполнения условий **Stable Base Exit Gate**.

---

## Status Overview

| Group | Status | Details |
|---|---|---|
| **P0: Data, Security, Reliability** | ✅ COMPLETED | `FND-001` - `FND-013` fully implemented and verified via unit & PostgreSQL integration tests |
| **P1: Reliability & Domain** | ✅ COMPLETED | `FND-014` - `FND-032` unified time policy, durable jobs worker, Telegram outbox worker, AI safety, GDPR lifecycle, single-call AI adapters, REQUIRES_NEW fix |
| **P2: Post-Stabilization Operations** | ✅ COMPLETED | `FND-033` - `FND-037` Spring Modulith boundaries, capacity evaluation, Docker readiness, product evaluation |

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
| `FND-010` | Сделать Telegram delivery надёжным (outbox ledger). | ✅ Completed (Flyway `V17`, `TelegramBotService` outbox ledger, 4000-char chunking, plain-text fallback, `TelegramOutboxWorker` retries, `sent_at` fix for failures) |
| `FND-011` | Ввести AI safety и output validation. | ✅ Completed (`AiSafetyService`, prompt injection defense, medical red flag detection, escaped XML boundary wrapping, `NutritionInsightResponse` range validation) |
| `FND-012` | Ограничить вызовы AI провайдеров и их стоимость. | ✅ Completed (`OpenRouterAdapter` & `GeminiAdapter` single billable call parsing without secondary re-generation) |
| `FND-013` | Закрепить PostgreSQL integration test baseline. | ✅ Completed (`mvn verify -Pintegration` Testcontainers gate) |

---

## P1: Продакшн-надёжность и доменная корректность

| ID | Задача | Статус |
|---|---|---|
| `FND-014` | Единая time policy (UTC Clock, user IANA timezone, Instant). | ✅ Completed (Global `Clock.systemUTC()` bean, `UserTimeService`, [ADR 005](adr/005-unified-time-policy.md), removed `Europe/Kiev`) |
| `FND-015` | Нормализовать reporting periods (`DateRange`). | ✅ Completed (Domain `DateRange` record supporting ISO week Monday-Sunday and calendar month bounds with leap year rules) |
| `FND-016` | Создать production scheduler model. | ✅ Completed (`NutritionSyncScheduler` with explicit UTC cron timezone, `Clock` bean, and `DurableJobUseCase` tracking) |
| `FND-017` | Исправить report coverage accuracy. | ✅ Completed (`NutritionDay` with `NutritionDataStatus` `PRESENT`, `MISSING_SYNC`, `NO_RECORDS`, `daysTracked` in `NutritionWeeklyStatsDto`) |
| `FND-018` | Исправить FatSecret profile sync `REQUIRES_NEW` self-invocation. | ✅ Completed (`FatSecretSingleProfileSyncer` bean proxy for transactional isolation) |
| `FND-019` | Ввести execution idempotency contract. | ✅ Completed (`idempotency_key` unique index and deduplication in `DurableJobService`) |
| `FND-020` | RFC-compliant CSV parsing & error recovery. | ✅ Completed (`JefitCsvParserAdapter` row limits, malformed line warnings, UTF-8/BOM support) |
| `FND-021` | Workout import merge semantics. | ✅ Completed (`WorkoutImportService` changed date tracking and non-destructive upsert) |
| `FND-022` | Domain invariants & database `CHECK` constraints. | ✅ Completed (Validated calorie/macro ranges and entity invariants) |
| `FND-023` | Notes/weight event lifecycle & response accuracy. | ✅ Completed (Accurate "processing received" Telegram responses, note/weight deletion events) |
| `FND-024` | Telegram command parser & private-chat security. | ✅ Completed (Strict command routing, argument validation, and private-chat guards) |
| `FND-025` | Auth/session contract & security error handling. | ✅ Completed (`SecurityConfig`, JWT validation, JSON error responses) |
| `FND-026` | Unified API errors & HTTP semantics. | ✅ Completed (`ApiErrorResponseWriter`, typed domain exceptions) |
| `FND-027` | AI freshness & memory provenance. | ✅ Completed (Snapshot hash validation, versioned prompts, isolated context) |
| `FND-028` | Observability & logging hygiene. | ✅ Completed (Privacy-safe structured logging with latency and status fields) |
| `FND-029` | Modulith event publication management. | ✅ Completed (Listener rethrow mechanics for Spring Modulith event retry ledger) |
| `FND-030` | Spring Boot & Spring AI stack alignment. | ✅ Completed (Spring Boot 3.4+, Java 21, clean dependency tree) |
| `FND-031` | CI & static analysis hygiene. | ✅ Completed (Code privacy scan hook, ArchUnit module boundary gate) |
| `FND-032` | Documentation as source of truth. | ✅ Completed (Clean Markdown encoding, ADRs, updated testing docs) |

---

## P2: Post-Stabilization Operations

| ID | Задача | Статус |
|---|---|---|
| `FND-033` | Modulith public contracts & boundary encapsulation. | ✅ Completed (Top-level package exposed interfaces) |
| `FND-034` | Infrastructure/Application layer separation. | ✅ Completed (Hexagonal port/adapter architecture) |
| `FND-035` | Vector search capacity benchmark. | ✅ Completed (pgvector HNSW index tuning) |
| `FND-036` | Production readiness & graceful execution. | ✅ Completed (Durable workers, outbox retry, health checks) |
| `FND-037` | Product-quality evaluation & regression safety. | ✅ Completed (Full unit, architecture, and integration test coverage) |

---

## Stable Base Exit Gate Status

- ✅ `mvn test` -> **BUILD SUCCESS**
- ✅ `mvn test -Parchitecture` -> **BUILD SUCCESS**
- ✅ `mvn verify -Pintegration` -> **BUILD SUCCESS**
- ✅ No external HTTP/AI network I/O inside database transactions.
- ✅ All durable jobs have status, `idempotency_key`, backoff retries, and stuck worker recovery.
- ✅ Ownership, authorization, GDPR export, and cascading account deletion enforced.
- ✅ Telegram responses reflect real processing status without false immediate success claims.
