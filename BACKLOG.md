# FitnessApp Fix Backlog

Generated: 2026-07-03

Summary: 30 fixes total: P0=6, P1=15, P2=9. Effort split: S=13, M=14, L=3.

## Оглавление

- [x] [FIX-001] Закрыть `/api/v1/week` от обычных пользователей
- [x] [FIX-002] Закрыть `/api/v1/month` от обычных пользователей
- [x] [FIX-003] Исправить VIP matcher для workout import
- [x] [FIX-004] Убрать raw Telegram message text из логов
- [x] [FIX-005] Обновить Spring AI из-за CVE-2026-22729
- [x] [FIX-006] Починить Spring Modulith cycles
- [x] [FIX-007] Обновить Tomcat/Spring Boot patch line
- [x] [FIX-008] Обновить JJWT до актуальной линии
- [x] [FIX-009] Добавить rate limit для Telegram `/ask`
- [x] [FIX-010] Ограничить brute force Telegram link codes
- [x] [FIX-011] Шифровать FatSecret OAuth tokens в БД
- [ ] [FIX-012] Восстановить HNSW index для `user_memory.embedding`
- [ ] [FIX-013] Синхронизировать pgvector table/dimension config
- [ ] [FIX-014] Добавить pgvector integration test для memory isolation
- [ ] [FIX-015] Добавить FK для `user_notes.user_id`
- [ ] [FIX-016] Валидировать nutrition date range
- [ ] [FIX-017] Исправить HTTP semantics sync endpoints
- [ ] [FIX-018] Добавить circuit breaker/backoff для AI router
- [ ] [FIX-019] Добавить cleanup expired `user_memory`
- [ ] [FIX-020] Добавить CI baseline
- [ ] [FIX-021] Добавить Actuator health/metrics
- [ ] [FIX-022] Привести Maven dependencies в порядок
- [ ] [FIX-023] Добавить реальный OpenAPI endpoint
- [ ] [FIX-024] Добавить README и env/runbook
- [ ] [FIX-025] Исправить `/api/v1/workout-analitic` typo совместимо
- [ ] [FIX-026] Заменить untyped controller responses на DTO
- [ ] [FIX-027] Удалить obsolete `TelegramLinkService`
- [ ] [FIX-028] Сделать Jefit CSV parser observable
- [ ] [FIX-029] Сделать workout `@ManyToOne` lazy
- [ ] [FIX-030] Вынести AI prompts в versioned resources

## Resolved since audit

- `.env` hygiene: `.gitignore` уже содержит `.env`, а `git ls-files .env` ничего не возвращает. Отдельный FIX на ignore не нужен.
- Dev-only test endpoints: `src/main/java/com/fit/fitnessapp/nutrition/adapter/in/web/TestController.java` уже помечен `@Profile("dev")`, а `src/main/java/com/fit/fitnessapp/auth/infrastructure/config/SecurityConfig.java` ограничивает `/test/**` ролью `ADMIN`. Отдельный production exposure FIX не нужен.
- `ApplicationPidFileWriter`: в `src/main/java`, `src/main/resources`, `pom.xml` и `docker-compose.yml` не найдено использования. Поэтому CVE-2026-40977 не выделен отдельным FIX; общий Spring Boot/Tomcat upgrade покрыт в FIX-007.

## P0 — Critical

## [FIX-001] Закрыть `/api/v1/week` от обычных пользователей

- **Приоритет**: P0
- **Категория**: security
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/analytics/port/in/WeeklyReportController.java`, `src/main/java/com/fit/fitnessapp/analytics/application/WeeklyReportOrchestrator.java`, `src/main/java/com/fit/fitnessapp/auth/infrastructure/config/SecurityConfig.java`, `src/test/java/com/fit/fitnessapp/auth/SecurityConfigWebTest.java`
- **Текущее состояние**: Любой authenticated user может вызвать `GET /api/v1/week`. Этот endpoint вызывает `WeeklyReportOrchestrator.generateWeeklyReports()`, который берёт `userApi.getAllUserIds()` и запускает генерацию для всех пользователей.
- **Целевое состояние**: Endpoint доступен только `ADMIN` или заменён на безопасный current-user flow без batch over all users. Для ближайшего локального фикса предпочтительно ограничить существующий batch endpoint ролью `ADMIN`.
- **Acceptance criteria**:
  - Добавлен MockMvc тест: authenticated user с ролью `USER` на `GET /api/v1/week` получает `403`.
  - Добавлен MockMvc тест: authenticated user с ролью `ADMIN` на `GET /api/v1/week` получает успешный ответ, а orchestrator вызывается ровно один раз.
  - Unauthenticated request на `GET /api/v1/week` возвращает `401`.
- **Effort**: S
- **Зависимости**: нет

## [FIX-002] Закрыть `/api/v1/month` от обычных пользователей

- **Приоритет**: P0
- **Категория**: security
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/analytics/port/in/MonthlyReportController.java`, `src/main/java/com/fit/fitnessapp/analytics/application/MonthlyReportOrchestrator.java`, `src/main/java/com/fit/fitnessapp/auth/infrastructure/config/SecurityConfig.java`, `src/test/java/com/fit/fitnessapp/auth/SecurityConfigWebTest.java`
- **Текущее состояние**: Любой authenticated user может вызвать `GET /api/v1/month`. Endpoint вызывает `MonthlyReportOrchestrator.generateMonthlyReports()`, который берёт `userApi.getAllUserIds()` и обрабатывает всех пользователей.
- **Целевое состояние**: Существующий all-users monthly batch endpoint доступен только `ADMIN`, либо переведён на безопасный current-user endpoint отдельным дизайном. Для базового фикса требуется role gate.
- **Acceptance criteria**:
  - Добавлен MockMvc тест: роль `USER` на `GET /api/v1/month` получает `403`.
  - Добавлен MockMvc тест: роль `ADMIN` на `GET /api/v1/month` получает успешный ответ, а orchestrator вызывается ровно один раз.
  - Unauthenticated request на `GET /api/v1/month` возвращает `401`.
- **Effort**: S
- **Зависимости**: нет

## [FIX-003] Исправить VIP matcher для workout import

- **Приоритет**: P0
- **Категория**: security
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/auth/infrastructure/config/SecurityConfig.java`, `src/main/java/com/fit/fitnessapp/workout/adapter/in/web/WorkoutImportController.java`, `src/test/java/com/fit/fitnessapp/auth/SecurityConfigWebTest.java`
- **Текущее состояние**: `SecurityConfig` ограничивает `/api/import/**`, но реальный controller path — `/api/v1/workout-import/import/{format}`. Поэтому bulk workout import фактически доступен любому authenticated user.
- **Целевое состояние**: Security matcher покрывает реальный path `/api/v1/workout-import/**`, а старый неиспользуемый matcher удалён или заменён.
- **Acceptance criteria**:
  - Добавлен MockMvc тест: роль `USER` на `POST /api/v1/workout-import/import/jefit` получает `403`.
  - Добавлен MockMvc тест: роль `VIP` на `POST /api/v1/workout-import/import/jefit` проходит security layer.
  - В `SecurityConfig` нет единственного matcher `/api/import/**` как защиты import API.
- **Effort**: S
- **Зависимости**: нет

## [FIX-004] Убрать raw Telegram message text из логов

- **Приоритет**: P0
- **Категория**: security
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/telegram/adapter/in/TelegramUpdateHandler.java`, `src/test/java/com/fit/fitnessapp/telegram/adapter/in/TelegramUpdateHandlerPrivacyLoggingTest.java`
- **Текущее состояние**: `TelegramUpdateHandler` логирует `Message from {}: {}` с полным `update.getMessage().getText()` и логирует text для unknown messages. Пользовательские заметки, вес и AI-вопросы могут попадать в plaintext logs.
- **Целевое состояние**: Логи Telegram содержат только metadata: `chatId`, command type, handler name, status/error code. Raw message text не логируется.
- **Acceptance criteria**:
  - Добавлен тест с `CapturedOutput`: входной Telegram text не появляется в logs.
  - `rg -n "Message from|No handler found for message|update.getMessage\\(\\).getText\\(\\)" src/main/java/com/fit/fitnessapp/telegram/adapter/in/TelegramUpdateHandler.java` не находит raw-text logging.
- **Effort**: S
- **Зависимости**: нет

## [FIX-005] Обновить Spring AI из-за CVE-2026-22729

- **Приоритет**: P0
- **Категория**: security
- **Затронутые файлы**: `pom.xml`, `src/main/java/com/fit/fitnessapp/memory/application/service/MemoryService.java`, `src/test/java/com/fit/fitnessapp/memory/MemoryServiceTest.java`
- **Текущее состояние**: Проект использует Spring AI `1.1.2`; advisory [CVE-2026-22729](https://spring.io/security/cve-2026-22729/) фиксируется в `1.1.3` для линии `1.1.x`. `MemoryService` использует `FilterExpressionBuilder.eq("user_id", userId)` как metadata isolation в pgvector/RAG.
- **Целевое состояние**: Все Spring AI artifacts обновлены минимум до `1.1.3` на совместимой линии, без regression в chat/vector store flows.
- **Acceptance criteria**:
  - `mvn dependency:tree "-Dincludes=org.springframework.ai"` показывает Spring AI `>= 1.1.3` и не показывает `1.1.2`.
  - `mvn test` проходит.
  - Добавлен или обновлён тест `MemoryService`, фиксирующий, что memory queries всегда строятся с server-side `userId`, а не с user-controlled metadata.
- **Effort**: M
- **Зависимости**: нет

## [FIX-006] Починить Spring Modulith cycles

- **Приоритет**: P0
- **Категория**: architecture
- **Затронутые файлы**: `src/test/java/com/fit/fitnessapp/module/ModuleArchitectureTest.java`, `src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java`, `src/main/java/com/fit/fitnessapp/analytics/WeeklyReportRequestedEvent.java`, `src/main/java/com/fit/fitnessapp/analytics/MonthlyReportRequestedEvent.java`, `src/main/java/com/fit/fitnessapp/exception/GlobalExceptionHandler.java`, `src/main/java/com/fit/fitnessapp/ai/exception/*`, `src/main/java/com/fit/fitnessapp/nutrition/domain/MissingFatSecretConnectionException.java`
- **Текущее состояние**: `mvn -Parchitecture test` падает. Подтверждённые cycles: `ai -> analytics -> auth -> exception -> ai`, `ai -> analytics -> nutrition -> auth -> exception -> ai`, `exception -> nutrition -> exception`, плюс `exception` зависит от non-exposed `ai.exception` types.
- **Целевое состояние**: Spring Modulith verification является рабочим quality gate, а shared events/exceptions живут в стабильных exposed packages или в отдельном module/interface package без обратных зависимостей.
- **Acceptance criteria**:
  - `mvn -Parchitecture test` проходит без `Violations`.
  - `GlobalExceptionHandler` не импортирует non-exposed classes из `com.fit.fitnessapp.ai.exception`.
  - `FitnessAiService` не создаёт прямой module cycle через analytics event types.
- **Effort**: L
- **Зависимости**: нет

## P1 — Base hardening

## [FIX-007] Обновить Tomcat/Spring Boot patch line

- **Приоритет**: P1
- **Категория**: security
- **Затронутые файлы**: `pom.xml`, `src/test/java/com/fit/fitnessapp/auth/SecurityConfigWebTest.java`
- **Текущее состояние**: Spring Boot parent `3.4.2` тянет `org.apache.tomcat.embed:tomcat-embed-core:10.1.34`. Эта версия попадает в диапазон [CVE-2025-24813](https://nvd.nist.gov/vuln/detail/CVE-2025-24813); эксплуатация требует нестандартной конфигурации, но Apache фиксирует issue в Tomcat `10.1.35` и выше.
- **Целевое состояние**: Проект использует Spring Boot patch line или explicit Tomcat override, где `tomcat-embed-core >= 10.1.35`, без изменения runtime behavior приложения.
- **Acceptance criteria**:
  - `mvn dependency:tree "-Dincludes=org.apache.tomcat.embed:tomcat-embed-core"` показывает `10.1.35` или новее.
  - `mvn test` проходит.
  - В коде/ресурсах по-прежнему нет настройки write-enabled DefaultServlet или file-based session persistence.
- **Effort**: M
- **Зависимости**: нет

## [FIX-008] Обновить JJWT до актуальной линии

- **Приоритет**: P1
- **Категория**: security
- **Затронутые файлы**: `pom.xml`, `src/main/java/com/fit/fitnessapp/auth/infrastructure/utils/JwtCore.java`, `src/test/java/com/fit/fitnessapp/auth/SecurityConfigWebTest.java`, `src/test/java/com/fit/fitnessapp/auth/AuthControllerTest.java`
- **Текущее состояние**: Проект использует `io.jsonwebtoken:*:0.11.5`. `versions:display-dependency-updates` показывает актуальную линию `0.13.0`.
- **Целевое состояние**: JWT stack обновлён до `0.13.0` или текущей совместимой stable версии, а token generation/validation покрыты тестами.
- **Acceptance criteria**:
  - `mvn dependency:tree "-Dincludes=io.jsonwebtoken"` не показывает `0.11.5`.
  - Тесты expired/invalid/valid JWT в `SecurityConfigWebTest` проходят.
  - `mvn test` проходит.
- **Effort**: M
- **Зависимости**: нет

## [FIX-009] Добавить rate limit для Telegram `/ask`

- **Приоритет**: P1
- **Категория**: security
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandler.java`, `src/main/java/com/fit/fitnessapp/ai/RateLimiterService.java`, `src/main/java/com/fit/fitnessapp/ai/RateLimitInterceptor.java`, `src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandlerTest.java`
- **Текущее состояние**: Web AI endpoints ограничены `RateLimitInterceptor` только для `/api/v1/ai/**`. Telegram `/ask` публикует `TelegramAskRequestedEvent` без общего лимита и может обходить web rate limit.
- **Целевое состояние**: Telegram `/ask` использует тот же per-user AI budget или отдельный явно настроенный token bucket до публикации AI event.
- **Acceptance criteria**:
  - Добавлен тест: после исчерпания лимита `AskCommandHandler` не публикует `TelegramAskRequestedEvent`.
  - Пользователь получает Telegram response о rate limit.
  - Существующие `RateLimiterServiceTest` и `RateLimitInterceptorTest` проходят.
- **Effort**: M
- **Зависимости**: нет

## [FIX-010] Ограничить brute force Telegram link codes

- **Приоритет**: P1
- **Категория**: security
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/telegram/application/service/TelegramLinkCodeManager.java`, `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/LinkCommandHandler.java`, `src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/LinkCommandHandlerTest.java`
- **Текущее состояние**: Link codes — 6 digits, TTL 10 минут, но нет лимита попыток на chat/code и нет lockout после repeated invalid codes.
- **Целевое состояние**: Invalid attempts ограничены per chat или per code; successful code invalidates immediately; repeated brute force не выполняет бесконечные lookups.
- **Acceptance criteria**:
  - Добавлен тест: после N invalid attempts handler возвращает limit/lockout response и не пытается link.
  - Добавлен тест: successful `/link <code>` инвалидирует code и повторное использование не работает.
- **Effort**: M
- **Зависимости**: нет

## [FIX-011] Шифровать FatSecret OAuth tokens в БД

- **Приоритет**: P1
- **Категория**: security
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/entity/FatSecretConnectionJpaEntity.java`, `src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/NutritionPersistenceAdapter.java`, `src/main/java/com/fit/fitnessapp/auth/adapter/out/persistence/repository/UserRepository.java`, `src/main/resources/db/migration/V1__init_schema.sql`, новая Flyway migration `src/main/resources/db/migration/V*_encrypt_fatsecret_tokens.sql`, `src/main/resources/application.properties`
- **Текущее состояние**: `access_token` и `access_token_secret` сохраняются и читаются как plaintext strings. При компрометации БД FatSecret OAuth credentials раскрываются напрямую.
- **Целевое состояние**: Tokens шифруются на application layer перед записью и расшифровываются только внутри persistence adapter. Ключ берётся из env/config, а отсутствие ключа в non-test профиле падает явно при startup.
- **Acceptance criteria**:
  - Добавлен persistence test: сохранённый DB column value не равен plaintext token.
  - Existing flows `saveToken()` и `getToken()` возвращают исходный `FatSecretToken`.
  - При отсутствии encryption key в production-like profile startup fails с понятной ошибкой.
- **Effort**: L
- **Зависимости**: нет

## [FIX-012] Восстановить HNSW index для `user_memory.embedding`

- **Приоритет**: P1
- **Категория**: data
- **Затронутые файлы**: `src/main/resources/db/migration/V6__structured_ai_and_pgvector.sql`, `src/main/resources/db/migration/V6.1__fix_dimention_and_index.sql`, новая Flyway migration `src/main/resources/db/migration/V*_recreate_user_memory_hnsw_index.sql`
- **Текущее состояние**: V6 создаёт HNSW index `idx_user_memory_embedding` на `vector(1536)`. V6.1 удаляет index и меняет column на `vector(2048)`, но не создаёт HNSW index заново.
- **Целевое состояние**: Новая migration восстанавливает `idx_user_memory_embedding` на `user_memory USING hnsw (embedding vector_cosine_ops)` после перехода на `vector(2048)`.
- **Acceptance criteria**:
  - Добавлена новая Flyway migration, не редактирующая старые V6/V6.1 files.
  - SQL check против Postgres/pgvector показывает наличие `idx_user_memory_embedding`.
  - `mvn test` проходит.
- **Effort**: S
- **Зависимости**: нет

## [FIX-013] Синхронизировать pgvector table/dimension config

- **Приоритет**: P1
- **Категория**: infra
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/memory/infrastructure/config/MemoryConfig.java`, `src/main/resources/application.properties`, `src/test/resources/application-test.properties`
- **Текущее состояние**: `MemoryConfig` создаёт vector store с `.vectorTableName("user_memory")` и `.dimensions(2048)`, но `application.properties` всё ещё содержит `spring.ai.vectorstore.pgvector.table-name=vector_store`. Комментарий в `MemoryConfig` про pgvector max dimension некорректен.
- **Целевое состояние**: Конфигурация не противоречит runtime bean: table name и dimension описаны в одном месте или явно совпадают с `user_memory`/`2048`.
- **Acceptance criteria**:
  - В properties нет misleading `vector_store` для активного memory table.
  - `MemoryConfig` не содержит некорректный комментарий про `pgvector max 2000`.
  - Добавлен config-level test или documented assertion, что runtime table name — `user_memory`.
- **Effort**: S
- **Зависимости**: FIX-012

## [FIX-014] Добавить pgvector integration test для memory isolation

- **Приоритет**: P1
- **Категория**: testing
- **Затронутые файлы**: `pom.xml`, `src/test/java/com/fit/fitnessapp/memory/MemoryPgVectorIntegrationTest.java`, `src/main/java/com/fit/fitnessapp/memory/application/service/MemoryService.java`, `src/main/resources/db/migration/*`
- **Текущее состояние**: Test profile использует H2 и не проверяет pgvector schema, vector index и metadata filter isolation. Риск CVE-2026-22729 и schema drift не ловится тестами.
- **Целевое состояние**: Integration profile поднимает Postgres с pgvector, прогоняет Flyway migrations и проверяет, что memory retrieval одного userId не возвращает документы другого userId.
- **Acceptance criteria**:
  - Добавлен integration test с pgvector container/image, который прогоняет Flyway migrations.
  - Test inserts memory docs для двух пользователей и доказывает isolation по `user_id`.
  - Test suite документирован так, чтобы запускался через `mvn -Pintegration verify` или отдельный profile.
- **Effort**: M
- **Зависимости**: FIX-005, FIX-012

## [FIX-015] Добавить FK для `user_notes.user_id`

- **Приоритет**: P1
- **Категория**: data
- **Затронутые файлы**: `src/main/resources/db/migration/V3__create_user_notes_table.sql`, новая Flyway migration `src/main/resources/db/migration/V*_add_user_notes_user_fk.sql`, `src/main/java/com/fit/fitnessapp/auth/adapter/out/persistence/entity/UserNote.java`
- **Текущее состояние**: `user_notes.user_id` хранится как plain `bigint not null`; в V3 нет FK на `users(id)`. Другие user-owned tables уже имеют foreign keys with cascade.
- **Целевое состояние**: Новая migration добавляет FK `user_notes.user_id -> users(id)` с явно выбранной delete policy, предпочтительно `ON DELETE CASCADE` для consistency с остальными user-owned данными.
- **Acceptance criteria**:
  - Новая migration добавляет FK без редактирования V3.
  - DB schema validation/Flyway migration проходит.
  - Добавлен persistence/integration test: note нельзя сохранить для nonexistent user или note удаляется при удалении user согласно выбранной policy.
- **Effort**: M
- **Зависимости**: нет

## [FIX-016] Валидировать nutrition date range

- **Приоритет**: P1
- **Категория**: data
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/nutrition/adapter/in/web/NutritionController.java`, `src/main/java/com/fit/fitnessapp/exception/GlobalExceptionHandler.java`, `src/test/java/com/fit/fitnessapp/nutrition/NutritionControllerValidationTest.java`
- **Текущее состояние**: `GET /api/v1/nutrition/range` принимает `from` и `to`, но не проверяет `from <= to`.
- **Целевое состояние**: Invalid range возвращает стандартный `400` error response до обращения в query use case.
- **Acceptance criteria**:
  - Добавлен test: `from=2026-07-10&to=2026-07-01` возвращает `400`.
  - Добавлен test: valid range вызывает `queryUseCase.getDateRange(currentUserId, from, to)`.
  - Error response использует общий формат `ApiError`.
- **Effort**: S
- **Зависимости**: нет

## [FIX-017] Исправить HTTP semantics sync endpoints

- **Приоритет**: P1
- **Категория**: architecture
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/nutrition/adapter/in/web/NutritionController.java`, `src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionService.java`, `src/test/java/com/fit/fitnessapp/nutrition/NutritionControllerTest.java`
- **Текущее состояние**: `POST /api/v1/nutrition/sync/today` и `/sync/current-month` выполняют `syncUseCase` синхронно, но возвращают `202 Accepted`.
- **Целевое состояние**: Для базового фикса endpoints остаются синхронными и возвращают `200 OK` с коротким status DTO. Настоящий async queue можно сделать отдельным future enhancement, но текущая ложная семантика должна исчезнуть.
- **Acceptance criteria**:
  - Controller tests ожидают `200 OK` для обоих sync endpoints.
  - Response body содержит typed status DTO без raw string-only contract.
  - `syncUseCase` всё ещё вызывается ровно один раз для current user.
- **Effort**: S
- **Зависимости**: нет

## [FIX-018] Добавить circuit breaker/backoff для AI router

- **Приоритет**: P1
- **Категория**: infra
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/ai/SmartAiRouter.java`, `src/main/java/com/fit/fitnessapp/ai/adapter/out/OpenRouterAdapter.java`, `src/main/java/com/fit/fitnessapp/ai/adapter/out/GeminiAdapter.java`, `src/test/java/com/fit/fitnessapp/ai/SmartAiRouterTest.java`
- **Текущее состояние**: `SmartAiRouter` перебирает fallback models на каждый вызов. После provider outage следующий запрос снова немедленно пробует тот же failing provider/model без cooldown.
- **Целевое состояние**: Для `AiUnavailableException` есть short-lived per-provider/model cooldown или Resilience4j circuit breaker. Fatal `AiAuthException`/`AiInvalidRequestException` по-прежнему останавливают OpenRouter loop.
- **Acceptance criteria**:
  - Добавлен test: повторный вызов внутри cooldown не вызывает модель, которая только что вернула `AiUnavailableException`.
  - Добавлен test: успешный вызов сбрасывает failure state.
  - Existing fallback tests для 429/fallback продолжают проходить.
- **Effort**: M
- **Зависимости**: нет

## [FIX-019] Добавить cleanup expired `user_memory`

- **Приоритет**: P1
- **Категория**: data
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/memory/application/service/MemoryService.java`, `src/main/java/com/fit/fitnessapp/memory/application/service/MemoryEventListener.java`, `src/main/java/com/fit/fitnessapp/memory/infrastructure/config/MemoryConfig.java`, новая repository/service/scheduler в `src/main/java/com/fit/fitnessapp/memory/**`, `src/test/java/com/fit/fitnessapp/memory/**`
- **Текущее состояние**: Expired memories фильтруются в Java по metadata `expires_at`, но физически остаются в `user_memory`. Со временем vector store растёт, а expired docs продолжают участвовать в approximate search до post-filter.
- **Целевое состояние**: Есть scheduled или explicit cleanup, который удаляет expired `user_memory` rows по metadata `expires_at`.
- **Acceptance criteria**:
  - Добавлен service/repository method, удаляющий rows с `expires_at < now`.
  - Добавлен test: expired memory удаляется, non-expired и long-term memory сохраняются.
  - Cleanup schedule configurable и может быть disabled in tests.
- **Effort**: M
- **Зависимости**: FIX-012

## [FIX-020] Добавить CI baseline

- **Приоритет**: P1
- **Категория**: infra
- **Затронутые файлы**: `.github/workflows/ci.yml`, `pom.xml`
- **Текущее состояние**: В репозитории нет `.github` workflow. Локально `mvn test` проходит, но архитектурный профиль падает и не является CI gate.
- **Целевое состояние**: CI запускает базовый Maven test suite на pull requests. После закрытия FIX-006 CI также запускает `mvn -Parchitecture test`.
- **Acceptance criteria**:
  - Добавлен GitHub Actions workflow с Java setup и Maven cache.
  - Workflow запускает `mvn test`.
  - После FIX-006 workflow запускает `mvn -Parchitecture test`.
- **Effort**: S
- **Зависимости**: FIX-006 для architecture gate

## [FIX-021] Добавить Actuator health/metrics

- **Приоритет**: P1
- **Категория**: infra
- **Затронутые файлы**: `pom.xml`, `src/main/resources/application.properties`, `src/main/java/com/fit/fitnessapp/auth/infrastructure/config/SecurityConfig.java`, `src/test/java/com/fit/fitnessapp/auth/SecurityConfigWebTest.java`
- **Текущее состояние**: В `pom.xml` нет `spring-boot-starter-actuator`; нет стандартных health/metrics endpoints для runtime checks.
- **Целевое состояние**: Actuator подключён с минимально безопасной exposure policy: health/readiness доступны согласно выбранной security policy, metrics/info защищены.
- **Acceptance criteria**:
  - `/actuator/health` доступен согласно documented policy и покрыт MockMvc test.
  - Sensitive actuator endpoints не открыты анонимно.
  - `mvn test` проходит.
- **Effort**: M
- **Зависимости**: нет

## P2 — Polish

## [FIX-022] Привести Maven dependencies в порядок

- **Приоритет**: P2
- **Категория**: infra
- **Затронутые файлы**: `pom.xml`
- **Текущее состояние**: `mvn dependency:analyze` проходит, но выдаёт много warnings: used undeclared dependencies, unused declared starters, и `org.junit.jupiter:junit-jupiter-api` как compile-scoped test-only dependency.
- **Целевое состояние**: Maven dependency hygiene не скрывает реальные drift issues: test-only dependencies имеют test scope, прямые compile imports объявлены явно или заменены starter-safe imports, а analyzer warnings документированы/снижены.
- **Acceptance criteria**:
  - `junit-jupiter-api` не находится в compile scope.
  - `mvn dependency:analyze` не содержит non-test scoped test-only dependency warnings.
  - Любые оставшиеся starter-related false positives явно documented in pom comments или Maven config.
- **Effort**: M
- **Зависимости**: FIX-005, FIX-007, FIX-008 желательно закрыть раньше, чтобы не чинить dependency tree дважды

## [FIX-023] Добавить реальный OpenAPI endpoint

- **Приоритет**: P2
- **Категория**: docs
- **Затронутые файлы**: `pom.xml`, `src/main/java/com/fit/fitnessapp/auth/adapter/in/web/UserNoteController.java`, `src/test/java/com/fit/fitnessapp/**`
- **Текущее состояние**: `UserNoteController` использует `io.swagger.v3.oas.annotations.*`, но `org.springdoc` dependency отсутствует. Аннотации приходят транзитивно через Spring AI, Swagger UI/OpenAPI endpoint фактически не настроен.
- **Целевое состояние**: Если OpenAPI нужен, добавить `springdoc-openapi-starter-webmvc-ui` и минимальную конфигурацию. Если не нужен, удалить Swagger annotations и транзитивную зависимость из surface area.
- **Acceptance criteria**:
  - Выбран один путь: либо `/v3/api-docs` доступен в test/profile, либо swagger annotations удалены.
  - Нет reliance на transitive `swagger-annotations-jakarta` из Spring AI.
  - `mvn test` проходит.
- **Effort**: S
- **Зависимости**: нет

## [FIX-024] Добавить README и env/runbook

- **Приоритет**: P2
- **Категория**: docs
- **Затронутые файлы**: `README.md`, `docker-compose.yml`, `src/main/resources/application.properties`, `src/test/resources/application-test.properties`
- **Текущее состояние**: `README.md` отсутствует. Env vars перечислены только в properties; локальный run flow, profiles, secrets, migrations и test commands не описаны в одном месте.
- **Целевое состояние**: README описывает локальный запуск, обязательные env vars, DB/pgvector, Telegram/FatSecret/AI keys, test commands, architecture profile и secret hygiene.
- **Acceptance criteria**:
  - `README.md` содержит команды для `docker compose up`, `mvn test`, `mvn -Parchitecture test`.
  - README перечисляет обязательные env vars без реальных secret values.
  - README объясняет, что `.env` ignored и не должен коммититься.
- **Effort**: S
- **Зависимости**: нет

## [FIX-025] Исправить `/api/v1/workout-analitic` typo совместимо

- **Приоритет**: P2
- **Категория**: docs
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/workout/adapter/in/web/WorkoutAnalyticController.java`, `src/test/java/com/fit/fitnessapp/workout/WorkoutAnalyticControllerTest.java`
- **Текущее состояние**: Controller path содержит typo: `/api/v1/workout-analitic`. Это закрепляет некорректный public API path.
- **Целевое состояние**: Добавить корректный path `/api/v1/workout-analytic` и временно сохранить старый alias для backward compatibility, если клиенты уже могли его использовать.
- **Acceptance criteria**:
  - Test подтверждает, что `/api/v1/workout-analytic/summary` работает.
  - Если alias оставлен, test подтверждает, что `/api/v1/workout-analitic/summary` ещё работает или отдаёт documented redirect/deprecation response.
  - README/OpenAPI после FIX-023/FIX-024 использует правильный path.
- **Effort**: M
- **Зависимости**: нет

## [FIX-026] Заменить untyped controller responses на DTO

- **Приоритет**: P2
- **Категория**: architecture
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/ai/AiController.java`, `src/main/java/com/fit/fitnessapp/auth/adapter/in/web/AuthController.java`, `src/test/java/com/fit/fitnessapp/ai/**`, `src/test/java/com/fit/fitnessapp/auth/AuthControllerTest.java`
- **Текущее состояние**: `AiController` и `AuthController` возвращают `ResponseEntity<?>`, `Map.of(...)` и raw string `"Registered successfully!"`. API contract не типизирован и сложнее документируется.
- **Целевое состояние**: Controller responses представлены Java records/DTO с явными fields, status codes и tests на JSON contract.
- **Acceptance criteria**:
  - `AiController` не содержит `ResponseEntity<?>` и raw `Map.of` response bodies.
  - `AuthController.register` возвращает typed DTO и корректный status (`201 Created` или documented `200 OK`).
  - Existing auth/AI controller tests обновлены под typed JSON contract и проходят.
- **Effort**: M
- **Зависимости**: нет

## [FIX-027] Удалить obsolete `TelegramLinkService`

- **Приоритет**: P2
- **Категория**: architecture
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/telegram/application/service/TelegramLinkService.java`, `src/main/java/com/fit/fitnessapp/telegram/application/service/TelegramLinkCodeManager.java`, `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/LinkCommandHandler.java`
- **Текущее состояние**: `TelegramLinkService` содержит cache annotations, `getExistingCode()` возвращает `null`, `verifyCode()` возвращает `Optional.empty()`. Реальный flow использует `TelegramLinkCodeManager`, поэтому service выглядит как stale implementation.
- **Целевое состояние**: Оставить один production link-code module. Удалить obsolete service или заменить usage так, чтобы не было dead/confusing code.
- **Acceptance criteria**:
  - `rg -n "TelegramLinkService|verifyCode\\(|getExistingCode\\(" src/main/java src/test/java` не находит obsolete service usage.
  - Link code tests продолжают проходить.
  - Spring context стартует без duplicate/confusing link code beans.
- **Effort**: S
- **Зависимости**: FIX-010 желательно закрыть раньше

## [FIX-028] Сделать Jefit CSV parser observable

- **Приоритет**: P2
- **Категория**: data
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/workout/adapter/out/parser/JefitCsvParserAdapter.java`, `src/test/java/com/fit/fitnessapp/workout/**`
- **Текущее состояние**: Parser содержит `catch (Exception ignored)` и может silently drop malformed rows/sets. Для import users это выглядит как успешный import с потерянными данными.
- **Целевое состояние**: Parser возвращает или логирует structured import warnings без raw sensitive dumps; caller может видеть количество skipped rows и причину.
- **Acceptance criteria**:
  - Добавлен parser test: malformed row не ломает весь import, но warning/skipped count доступен.
  - В main parser code нет `catch (Exception ignored)`.
  - Import response или service result содержит count imported/skipped.
- **Effort**: M
- **Зависимости**: нет

## [FIX-029] Сделать workout `@ManyToOne` lazy

- **Приоритет**: P2
- **Категория**: data
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/entity/WorkoutExerciseJpaEntity.java`, `src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/entity/WorkoutSetJpaEntity.java`, `src/test/java/com/fit/fitnessapp/workout/**`
- **Текущее состояние**: Workout JPA entities используют `@ManyToOne` без `fetch = FetchType.LAZY`, значит JPA default `EAGER` может создавать лишние fetches.
- **Целевое состояние**: Associations, которые не нужны всегда, явно `LAZY`; query adapters сами выбирают нужный fetch shape.
- **Acceptance criteria**:
  - `WorkoutExerciseJpaEntity` и `WorkoutSetJpaEntity` используют `@ManyToOne(fetch = FetchType.LAZY)`.
  - Existing persistence/import tests проходят.
  - Нет LazyInitializationException в tested query flows.
- **Effort**: S
- **Зависимости**: нет

## [FIX-030] Вынести AI prompts в versioned resources

- **Приоритет**: P2
- **Категория**: architecture
- **Затронутые файлы**: `src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java`, `src/main/resources/ai/prompts/**`, `src/test/java/com/fit/fitnessapp/ai/**`
- **Текущее состояние**: Daily/weekly/monthly/ask prompts зашиты прямо в `FitnessAiService`. Изменения prompts смешиваются с orchestration logic, нет версии prompt contract и простого prompt snapshot test.
- **Целевое состояние**: Prompts живут в resource templates с version metadata; service только подставляет context. Prompt rendering покрыт unit/snapshot tests без вызова внешних AI providers.
- **Acceptance criteria**:
  - В `FitnessAiService` нет больших hard-coded prompt text blocks.
  - Добавлены tests на rendering daily/weekly/monthly prompt templates с fixture context.
  - Prompt files имеют version/name metadata или naming convention, пригодный для будущих evals.
- **Effort**: L
- **Зависимости**: FIX-006 желательно закрыть раньше
