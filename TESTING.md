# FitnessApp Testing Plan

Last updated: 2026-05-15

This document replaces the old goal of "make every old test pass" with a practical testing strategy. The current tests are useful as clues, but not all of them should be preserved exactly. A test suite is a specification; if the specification is stale, fix the specification first.

## Testing Principles

1. Test current intended behavior, not old implementation accidents.
2. Prefer small tests first: pure unit tests are faster to understand and cheaper to maintain.
3. Use integration tests only where Spring, SQL, security, or external boundaries matter.
4. Do not use H2 for PostgreSQL-specific Flyway/JPA behavior. Use Testcontainers PostgreSQL.
5. Avoid brittle assertions on localized error-message text. Prefer exception type, status code, error code, and important fields.
6. Each test should answer one clear question.

## Test Types For This Project

### Unit Tests

Use for:

- rate limiting logic
- prompt/context builders
- DTO mappers
- command handlers with mocked dependencies
- service branching and validation logic

Tools:

- JUnit 5
- AssertJ
- Mockito

### Web/API Tests

Use for:

- auth endpoint behavior
- request validation
- HTTP status codes
- security rules
- JSON response shape

Tools:

- `@WebMvcTest` where possible
- `MockMvc`
- mocked service/use-case dependencies

### Persistence Tests

Use for:

- Flyway migrations
- JDBC queries
- JPA mappings
- PostgreSQL-specific SQL/types/indexes

Tools:

- Testcontainers PostgreSQL
- `@SpringBootTest` or sliced JDBC/JPA tests where practical

### Architecture Tests

Use for:

- Spring Modulith boundaries
- forbidden module dependencies
- module documentation generation

Tools:

- Spring Modulith `ApplicationModules`

Run these through the dedicated architecture gate, not through the default push gate.

## Existing Test Audit

### `src/test/java/com/fit/fitnessapp/ai/RateLimiterServiceTest.java`

Decision: KEEP, CLEAN

Why:

- Tests real current behavior.
- Fast unit test.
- No Spring context.

Actions:

- Clean corrupted display names/comments.
- Keep tests for same-user bucket reuse, different-user buckets, initial token availability, and limit enforcement.
- Consider renaming `sameuserIdReturnsSameBucket` to `sameUserIdReturnsSameBucket`.

### `src/test/java/com/fit/fitnessapp/ai/RateLimitInterceptorTest.java`

Decision: KEEP, REVISIT SECURITY EXPECTATION

Why:

- Fast unit test with mocks.
- Covers important request behavior.

Concern:

- Current behavior allows unauthenticated requests through when `CurrentUserApi` throws. That may be intentional for non-AI routes, but it is dangerous if used for public/auth endpoints.

Actions:

- Clean display names/comments.
- Keep current tests while behavior remains.
- Add a future issue to rate-limit public endpoints by IP address.

### `src/test/java/com/fit/fitnessapp/ai/SmartAiRouterTest.java`

Decision: KEEP, CLEAN

Why:

- Tests meaningful fallback behavior.
- Fast Mockito test.

Actions:

- Clean corrupted strings.
- Assert exception types and provider calls.
- Add missing cases:
  - all OpenRouter models unavailable, Gemini succeeds
  - all providers unavailable
  - invalid request aborts OpenRouter fallback

### `src/test/java/com/fit/fitnessapp/ai/OpenRouterAdapterTest.java`

Decision: REWRITE

Why:

- The behavior is important, but the test is brittle and currently mismatches implementation messages.
- It asserts localized text that has already changed.
- It also reveals a design issue: `OpenRouterAdapter` treats the first provider failure like a parsing failure and calls the provider a second time.

Actions:

- First decide desired adapter behavior.
- Rewrite tests around:
  - 401/403 -> `AiAuthException`
  - 400 -> `AiInvalidRequestException`
  - timeout/5xx -> `AiUnavailableException`
  - malformed structured output -> degraded response or specific parse exception, depending on desired design
- Assert no unnecessary second provider call for auth/400 errors.

### `src/test/java/com/fit/fitnessapp/nutrition/NutritionMonthlyApiTest.java`

Decision: REWRITE AS JDBC ADAPTER UNIT TEST

Why:

- It is trying to test `NutritionJdbcQueryAdapter` without Spring, which is good.
- Current mocking of `ResultSet.next()` inside a `RowCallbackHandler` is awkward and caused a stale failing expectation.

Actions:

- Rename to `NutritionJdbcQueryAdapterTest`.
- Use clearer fake data setup.
- Verify:
  - monthly aggregate mapping
  - daily/weekly breakdown mapping
  - empty breakdown
  - null aggregate behavior, if applicable
- Do not test SQL correctness here. Test SQL correctness later with PostgreSQL integration tests.

### `src/test/java/com/fit/fitnessapp/jdbc/workout/WorkoutQueryUseCaseTest.java`

Decision: REWRITE AS POSTGRES INTEGRATION TEST LATER

Why:

- This is a real integration test and needs a real database.
- It currently depends on full Spring context and H2, but migrations use PostgreSQL-specific types.
- Package path typo `workoout` was removed; keep future workout JDBC tests under `workout`.

Actions:

- Disable or delete until Testcontainers PostgreSQL is introduced.
- Recreate as `WorkoutQueryIntegrationTest`.
- Use Testcontainers.
- Verify weekly volume/reps aggregation with multiple exercises, multiple dates, and another user's workout to prove user isolation.

### `src/test/java/com/fit/fitnessapp/FitnessAppApplicationTests.java`

Decision: DELETE OR REPLACE

Why:

- Empty `contextLoads()` is low value.
- It currently fails because the test environment is wrong, not because it proves a useful behavior.

Actions:

- Delete for now, or replace later with a real smoke test using Testcontainers and explicit test profile.
- Do not let this block useful unit tests.

### `src/test/java/com/fit/fitnessapp/module/ModuleArchitectureTest.java`

Decision: KEEP IN ARCHITECTURE PROFILE

Why:

- This is valuable. It detects module cycles and leaked internals.
- But it should not block the fast push gate.

Actions:

- Run through `mvn test -Parchitecture`.
- Keep excluded from the default `mvn test` gate by naming convention.
- Split documentation generation from verification. Docs generation should not be a normal unit test assertion.

### `src/test/java/com/fit/fitnessapp/AuthTest.java`

Decision: DELETE COMMENTED BLOCK, RECREATE AS FOCUSED AUTH TESTS

Why:

- The real test class is commented out.
- The file contains old learning notes and corrupted comments.
- It does not currently run, so it creates false confidence.

Actions:

- Move useful notes elsewhere if you want to keep them.
- Delete the commented test block.
- Recreate auth coverage in smaller tests:
  - `AuthControllerWebTest`
  - `RegisterServiceTest`
  - `JwtCoreTest`
  - `SecurityRulesWebTest`

## Step-By-Step Testing Roadmap

## Step 1 - Stabilize The Test Folder

Goal:

Make it obvious which tests are active and which are intentionally deferred.

Tasks:

- Clean corrupted comments/display names in tests you keep.
- Delete `AuthTest.java` or replace it with a short TODO test skeleton.
- Keep Modulith checks tagged by naming convention as `*ArchitectureTest`.
- Delete or replace `FitnessAppApplicationTests`.
- Keep workout JDBC tests under the correctly spelled `workout` package.

Done when:

- No test file is mostly commented-out learning notes.
- Running the stable unit subset is possible.

Current command:

```bash
mvn test
```

## Step 2 - Create A Fast Unit Test Baseline

Goal:

Have a small set of tests that run quickly and teach you the code.

First tests to keep/write:

- `RateLimiterServiceTest`
- `RateLimitInterceptorTest`
- `SmartAiRouterTest`
- `MoeOrchestratorTest`

Add next:

- `TelegramLinkCodeManagerTest`
- `ConversationStateServiceTest`
- `WeightCommandHandlerTest`
- `AskCommandHandlerTest`

Done when:

- The AI routing/rate-limit/Telegram command basics are covered without starting Spring.

## Step 3 - Auth Tests

Goal:

Protect login/register/security behavior.

Tests to create:

- `RegisterServiceTest`
  - hashes password
  - assigns default role
  - rejects duplicate username/email
- `LoginServiceTest`
  - valid credentials return JWT
  - invalid credentials throw/return auth failure
- `JwtCoreTest`
  - generated token can be parsed
  - expired/invalid token is rejected
- `AuthControllerWebTest`
  - `/auth/register` returns expected status
  - `/auth/login` returns token
  - invalid body returns validation error after validation is added
- `SecurityRulesWebTest`
  - `/auth/**` is public
  - `/test/**` requires admin
  - protected API requires authentication

Done when:

- You can change auth code without guessing whether login/register still work.

## Step 4 - Nutrition Tests

Goal:

Protect FatSecret sync and query behavior.

Unit tests:

- `NutritionServiceTest`
  - missing FatSecret token throws clear exception
  - `syncDay` saves nutrition day and publishes `NutritionSyncedEvent`
  - `syncMonth` saves monthly summary and attempts full day syncs

Adapter tests:

- `NutritionJdbcQueryAdapterTest`
  - maps daily summary
  - maps monthly aggregate
  - maps breakdown rows

Integration tests later:

- Flyway creates nutrition tables in PostgreSQL.
- Unique constraints prevent duplicate day rows.
- Query adapter returns correct results from real SQL.

Done when:

- Nutrition behavior is covered at service and query mapping level.

## Step 5 - Workout Tests

Goal:

Protect workout import and analytics.

Unit tests:

- `WorkoutImportServiceTest`
  - imports valid workout
  - ignores/rejects duplicate external IDs, depending on intended behavior
  - validates missing exercise/set data

Integration tests:

- `WorkoutQueryIntegrationTest` with PostgreSQL Testcontainers
  - weekly volume aggregation
  - date filtering
  - user isolation
  - multiple exercises and sets

Done when:

- Workout analytics can be refactored safely.

## Step 6 - AI Insight Tests

Goal:

Protect expensive and fragile AI workflows without calling real AI providers.

Unit tests:

- `OpenRouterAdapterTest` rewritten after adapter design is fixed.
- `GeminiAdapterTest`
- `MoeOrchestratorTest`
- `FitnessAiServiceTest` split by behavior, or after `FitnessAiService` is refactored:
  - daily insight skipped when already exists
  - daily insight skipped when no nutrition data
  - daily insight saved and event published
  - weekly/monthly report saved and event published

Prompt tests:

- Prompt includes required fields.
- Prompt does not include raw secrets.
- Prompt templates render with all variables.

Done when:

- AI behavior is tested without real network calls.

## Step 7 - Telegram Tests

Goal:

Protect bot command behavior.

Tests:

- `/link` with valid code links account
- `/link` with invalid/expired code fails clearly
- `/ask` requires linked account
- `/ask question` publishes `TelegramAskRequestedEvent`
- `/weight` starts state flow
- weight input validates numeric range and publishes event
- dev-only `/test_generate` is not active outside dev profile

Done when:

- Telegram behavior is understandable without manually clicking through the bot.

## Step 8 - PostgreSQL Integration Test Foundation

Goal:

Stop fighting H2 for PostgreSQL behavior.

Tasks:

- Add Testcontainers dependency.
- Create base integration test class with PostgreSQL container.
- Configure datasource dynamically with `@DynamicPropertySource`.
- Run Flyway against the container.
- Move DB-heavy tests to `*IntegrationTest`.

Done when:

- Flyway migrations are tested against PostgreSQL.
- H2 is no longer used for PostgreSQL-specific behavior.

## Step 9 - Architecture Tests

Goal:

Make Spring Modulith verification useful again.

Tasks:

- Fix module API exposure with `@NamedInterface`.
- Remove cycles deliberately.
- Keep `ModuleArchitectureTest.verifyArchitecture()` green in the architecture profile.
- Keep documentation generation separate from normal test verification.

Done when:

- Modulith test passes and protects the intended boundaries.

## Step 10 - CI Strategy

Goal:

Run the right tests automatically.

Stages:

1. Fast unit tests on every push.
2. Web tests after controller/security stabilization.
3. PostgreSQL integration tests after Testcontainers setup.
4. Architecture tests after module cleanup.

Maven gates:

- `mvn test` runs the fast default gate on every push. It includes `*Test` and excludes `*IntegrationTest` and `*ArchitectureTest`.
- `mvn verify -Pintegration` runs future PostgreSQL/Testcontainers checks named `*IntegrationTest`.
- `mvn test -Parchitecture` runs Modulith checks named `*ArchitectureTest`.

Do not add feature work until the default `mvn test` gate is green.

## Immediate Next Actions

Do these in order:

1. Clean and keep `RateLimiterServiceTest`.
2. Clean and keep `RateLimitInterceptorTest`.
3. Clean and keep `SmartAiRouterTest`.
4. Rewrite or temporarily disable `OpenRouterAdapterTest`.
5. Delete or replace `FitnessAppApplicationTests`.
6. Keep `ModuleArchitectureTest.verifyArchitecture()` out of the default gate and runnable with `mvn test -Parchitecture`.
7. Delete the commented-out `AuthTest.java` and recreate auth tests from scratch.
8. Create the first stable command:

```bash
mvn test
```

After that, start building new tests module by module.
