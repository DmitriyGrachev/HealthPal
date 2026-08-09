# FitnessApp Stable Base Backlog

Цель документа: зафиксировать работы, необходимые для создания устойчивой базы проекта перед расширением функциональности.

Приоритеты:

- `P0` - блокеры расширения и production readiness.
- `P1` - обязательная production foundation.
- `P2` - архитектурное и эксплуатационное развитие после стабилизации.

## P0: Блокеры

| ID | Задача | Критерий готовности |
|---|---|---|
| `FND-001` | Исключить потерю nutrition-данных. FatSecret 2xx с error/malformed body может превратиться в пустой месяц, после чего локальные дни удаляются: [FatSecretApiAdapter.java](../src/main/java/com/fit/fitnessapp/nutrition/adapter/out/api/FatSecretApiAdapter.java#L274), [NutritionService.java](../src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionService.java#L80). | Typed outcomes `VALID`, `AUTHORITATIVE_EMPTY`, `PROVIDER_ERROR`, `MALFORMED`; удаление только по полному подтверждённому snapshot; при ошибке нет writes/events. Fixture-тесты, `mvn test`. |
| `FND-002` | Синхронизировать registration API и schema: API допускает username 64/email 255, БД только 50; email не unique: [RegisterRequest.java](../src/main/java/com/fit/fitnessapp/auth/domain/RegisterRequest.java#L8), [V1__init_schema.sql](../src/main/resources/db/migration/V1__init_schema.sql#L2). | Правила normalization/case sensitivity; одинаковые размеры в DTO/entity/DDL; DB uniqueness; race возвращает `409`; concurrent PostgreSQL tests. |
| `FND-003` | Завершить ownership schema. Нет FK `fatsecret_day`, `workout`, `workout_cardio -> users`; некоторые child FK nullable. | Forward-only migration, аудит orphan rows, `NOT NULL`, FK/cascade; тест неизвестного user и полного удаления aggregate. |
| `FND-004` | Реализовать lifecycle персональных данных: export, FatSecret disconnect, account deletion, очистка notes, workouts, nutrition, Telegram и vector memory. | Один use case со статусом; deterministic memory IDs; удаление note удаляет memory; сквозной PostgreSQL lifecycle test. |
| `FND-005` | Исправить dev runtime: [application-dev.properties](../src/main/resources/application-dev.properties#L47) использует отсутствующую `vector_store` и HNSW для `vector(2048)`, хотя production использует `user_memory` и exact scan. | Одинаковая schema/config во всех профилях; dev-profile startup smoke с Flyway и pgvector. |
| `FND-006` | Ввести durable job lifecycle. AI listeners проглатывают provider errors, поэтому Modulith считает publication завершённой: [FitnessAiService.java](../src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java#L127). | `PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED`, attempts, backoff, terminal failure, operator retry, recovery после restart; event-publication integration test. |
| `FND-007` | Убрать внешний I/O из DB-транзакций и Telegram update thread: AI, embeddings, FatSecret и CSV parsing пересекаются с `@Transactional`. | `read/parse -> validate -> short persist transaction -> after-commit event`; provider port вызывается без active transaction; HTTP/Telegram получают `jobId`. |
| `FND-008` | Создать production Telegram Link workflow. Сейчас код генерируется только dev-командой; Caffeine state теряется при restart, возможны collision и double-consume. | Authenticated endpoint для Current User; hashed TTL code в shared storage; atomic one-time consume; relink/unlink; expiry/collision/concurrency tests. |
| `FND-009` | Запретить утечку данных через group chats. Команды ищут связь по `telegramId`, но отвечают в текущий `chatId`: [AskCommandHandler.java](../src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandler.java#L35). | Персональные команды только в private chat; chat совпадает с сохранённой связью; group/channel не публикует события и данные. |
| `FND-010` | Сделать Telegram delivery надёжным. [TelegramBotService.java](../src/main/java/com/fit/fitnessapp/telegram/application/service/TelegramBotService.java#L25) проглатывает ошибки, после чего durable listener считается успешным. | Delivery ledger/outbox; retry 429/5xx; permanent failure state; idempotency; escaping/plain-text fallback; chunking длинных сообщений. |
| `FND-011` | Ввести AI safety и output validation. Prompts называют модель профессиональным диетологом, untrusted notes/questions вставляются напрямую, ограничения DTO существуют только в комментариях. | Safety policy, prompt trust boundaries, insufficient-data response, medical red flags, server-side validation всех полей; injection/medical/malformed fixtures. |
| `FND-012` | Ограничить provider calls, latency и стоимость. Parsing failure вызывает второй billable call, затем возможен перебор многих моделей: [OpenRouterAdapter.java](../src/main/java/com/fit/fitnessapp/ai/adapter/out/OpenRouterAdapter.java#L35). | Парсинг одного ответа без повторной генерации; typed 400/401/429/timeout; overall deadline, max attempts, bulkhead, per-user/global budget; call-count tests. |
| `FND-013` | Сделать PostgreSQL gate обязательным release criterion. | `mvn verify -Pintegration` стабильно зелёный в CI; Flyway/startup smoke; FK, cascade, concurrency, event replay и pgvector проверяются только на PostgreSQL. |

## P1: Production Foundation

| ID | Задача | Критерий готовности |
|---|---|---|
| `FND-014` | Единая time policy. Убрать hardcoded `Europe/Kiev`, server-local `now()` и смесь `TIMESTAMP/TIMESTAMPTZ/LocalDateTime`. | ADR, user IANA timezone, injected `Clock`, `Instant` для абсолютного времени, DST и PostgreSQL round-trip tests. |
| `FND-015` | Исправить reporting periods. `THIS_WEEK` сейчас означает rolling 7 дней, `THIS_MONTH` rolling месяц. | Общий `DateRange/ReportingPeriod`; calendar/rolling semantics; Monday, month/year boundary tests. |
| `FND-016` | Создать production scheduler model. | DB claim/distributed lock, pagination, bounded concurrency, graceful shutdown, persisted run summary; два workers не дублируют provider calls. |
| `FND-017` | Исправить report coverage. Missing days смешиваются с нулевыми значениями; weekly не передаёт `daysTracked`. | `missing != zero`; tracked/expected days; no-data не вызывает AI; prompts говорят об association, не causality. |
| `FND-018` | Исправить FatSecret profile/OAuth workflow. Ошибки проглатываются, success counter неверен, `REQUIRES_NEW` не действует при self-invocation. | Typed outcome; отдельный worker/transaction boundary; shared atomic OAuth state; configurable URLs/timeouts; truthful counters. |
| `FND-019` | Ввести concurrency/idempotency contract. | PostgreSQL upsert или bounded retry; execution keys `user+date+operation`; duplicate event создаёт одну запись и одно уведомление. |
| `FND-020` | Заменить ручной CSV parsing: [JefitCsvParserAdapter.java](../src/main/java/com/fit/fitnessapp/workout/adapter/out/parser/JefitCsvParserAdapter.java#L48). | Проверенная CSV library либо RFC fixture suite: BOM, delimiters, escaped quotes, multiline, malformed rows, file/row limits. |
| `FND-021` | Определить semantics workout import: `FULL_SNAPSHOT` против `INCREMENTAL_MERGE`; исправить identity exercise log. | Partial import ничего не удаляет; full snapshot удаляет только внутри scope; composite identity tests. |
| `FND-022` | Зафиксировать domain invariants. | Диапазоны weight/reps/calories/duration/profile в command/domain и DB `CHECK`; отсутствующие provider fields не превращаются молча в zero. |
| `FND-023` | Завершить notes/weight correctness. | Валидация ranges, note update/delete events, stable memory provenance; deterministic precedence `MANUAL/FATSECRET`; atomic weight upsert. |
| `FND-024` | Централизовать Telegram command parser/state machine. | Exact commands, `/cancel`, TTL, command priority, update-id deduplication, private-chat state key, recovery повреждённого state. |
| `FND-025` | Закончить auth/session contract. | Typed token response с expiry/type; stable user ID subject, issuer/audience; решение по refresh/logout/revocation/key rotation; удалённый user всегда получает JSON `401`. |
| `FND-026` | Унифицировать API errors и HTTP semantics. | Typed exceptions вместо проверки message strings; `201/202/409`; единый `ApiError` из controllers/filters/interceptors; pagination и contract tests. |
| `FND-027` | Исправить AI freshness и memory provenance. | Fingerprint включает data, prompt/model/schema/profile/context version; generated AI text не становится user fact; metadata filters выполняются в storage. |
| `FND-028` | Добавить observability. | Metrics/traces: job result, provider/model/attempt/latency/tokens/cost, event backlog, Telegram delivery, cleanup; correlation ID и alert thresholds. |
| `FND-029` | Обслуживать `event_publication`. | Dashboard старых incomplete events, retention completed rows, inspect/retry runbook; cleanup никогда не удаляет incomplete publication. |
| `FND-030` | Согласованно обновить Spring stack. Boot 3.4.13 завершил OSS support; Spring AI 1.1.8 и Modulith 1.4.12 доступны. | Один совместимый upgrade set, dependency tree без смешанных линий, rollback note, все три gates. [Boot](https://spring.io/blog/2025/12/18/spring-boot-3-4-13-available-now/), [Spring AI](https://spring.io/blog/2026/06/12/spring-ai-1-1-8-1-0-9-avaialble-now/), [Modulith](https://docs.spring.io/spring-modulith/reference/1.4/index.html). |
| `FND-031` | Усилить CI и testing policy. | Maven Wrapper, blocking SCA/SBOM, coverage baseline, static analysis; заменить 604-строчный source-string `CodeHygieneTest` реальными structural rules. |
| `FND-032` | Восстановить документацию как source of truth. | Исправить encoding `BACKLOG.md`; удалить устаревшие immediate actions из `TESTING.md`; актуальная workflow/test matrix и один активный backlog. |

## P2: После стабилизации

| ID | Задача | Результат |
|---|---|---|
| `FND-033` | Разнести глобальный `api` module по publisher-owned public contracts. | Локальная ownership событий, явные allowed dependencies, зелёный Modulith gate. |
| `FND-034` | Декомпозировать 541-строчный `FitnessAiService` и убрать infrastructure leaks из application layer. | Отдельные daily/weekly/monthly services, repository/provider ports, characterization tests. |
| `FND-035` | Провести capacity benchmark pgvector exact search. | Зафиксированный объём memories/user и latency SLO; только затем выбирать smaller vector, `halfvec` или ANN. |
| `FND-036` | Подготовить production operations. | Docker image, backup/restore drill, secret rotation, migration rollback policy, readiness и graceful shutdown. |
| `FND-037` | Ввести product-quality evaluation. | User feedback на insights, prompt/model version, evidence coverage, usefulness/safety metrics и regression dataset. |

## Порядок работ

1. Сначала `FND-001`, `FND-002`, `FND-003`, `FND-005`, `FND-013`: остановить риск потери данных и получить доверенный PostgreSQL baseline.
2. Затем `FND-004`, `FND-008`, `FND-009`: ownership, privacy и Telegram identity.
3. После этого `FND-006`, `FND-007`, `FND-010`, `FND-012`: durable jobs, короткие транзакции и delivery.
4. Далее time, schedulers, FatSecret и Jefit: `FND-014` - `FND-024`.
5. Затем AI safety, freshness, memory, observability и dependencies: `FND-011`, `FND-025` - `FND-032`.
6. Архитектурную декомпозицию `FND-033` - `FND-037` выполнять последней, после закрепления поведения тестами.

## Stable Base Exit Gate

- Все три Maven gate обязательны и зелёные:
  - `mvn test`
  - `mvn test -Parchitecture`
  - `mvn verify -Pintegration`
- Dev и production-like profile стартуют на чистой БД.
- Нет внешнего network I/O внутри DB transaction.
- Любая job имеет статус, idempotency key, retry и operator recovery.
- У каждого пользовательского объекта есть owner, export и deletion path.
- Нет ложных Telegram/API подтверждений успеха.
- Проверены два сквозных сценария:
  - `sync/import -> insight -> memory -> delivery`
  - `Telegram command -> durable save -> confirmation`

## Текущий baseline

Последняя подтверждённая проверка перед созданием документа:

- `mvn test`: 191 тест, без ошибок.
- `mvn test -Parchitecture`: успешно, один documentation test отключён намеренно.
- `mvn verify -Pintegration`: 12 PostgreSQL/Testcontainers тестов, без ошибок.

Зелёный baseline подтверждает текущее поведение, но не закрывает задачи из этого backlog автоматически.
