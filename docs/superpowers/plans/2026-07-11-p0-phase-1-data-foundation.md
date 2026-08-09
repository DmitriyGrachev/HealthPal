# P0 Phase 1 Data Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `FND-001`, `FND-002`, `FND-003`, `FND-005`, and `FND-013` so destructive nutrition reconciliation is authoritative and the registration, ownership, pgvector, and PostgreSQL release contracts are enforced.

**Architecture:** Keep provider response classification inside the nutrition outbound adapter and expose a small typed domain result to the application service. Normalize registration identity at the application boundary, rely on PostgreSQL constraints for race-safe uniqueness, and add one forward-only migration for identity and ownership invariants. Extend the shared pgvector Testcontainers suite and existing CI integration gate rather than adding a second test stack.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Data JPA/JDBC, Flyway, PostgreSQL 16 with pgvector, JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers, Maven.

## Global Constraints

- Existing Flyway migrations are immutable; add only forward migrations.
- Tests must not call FatSecret, AI providers, or Telegram.
- PostgreSQL-specific behavior must be tested with Testcontainers, never H2.
- Username is trimmed and case-sensitive with a maximum of 64 characters.
- Email is trimmed, lower-cased with `Locale.ROOT`, and limited to 255 characters.
- Nutrition writes/events are forbidden for provider-error, malformed, HTTP, and transport failures.
- Run `mvn test`, `mvn test -Parchitecture`, and `mvn verify -Pintegration` before declaring the phase complete.

---

## File Map

- `nutrition/domain/NutritionMonthFetchResult.java`: provider-neutral typed monthly outcome and validated snapshot payload.
- `nutrition/application/port/out/FatSecretApiPort.java`: changes the monthly fetch return type.
- `nutrition/adapter/out/api/FatSecretApiAdapter.java`: classifies FatSecret 2xx payloads without confusing missing structure with empty data.
- `nutrition/application/service/NutritionService.java`: permits writes only for authoritative outcomes.
- `auth/application/service/RegisterService.java`: canonicalizes identity before persistence.
- `auth/adapter/out/persistence/UserPersistenceAdapter.java`: maps pre-check and database-race duplicates to the same domain exception.
- `auth/adapter/out/persistence/entity/user/User.java`: mirrors database length/nullability/uniqueness metadata.
- `exception/GlobalExceptionHandler.java`: returns HTTP 409 for registration conflicts.
- `db/migration/V14__enforce_identity_and_ownership.sql`: widens identity columns, normalizes/audits email, and adds ownership/child constraints.
- `application-dev.properties`: aligns dev pgvector properties with production and Flyway.
- Focused unit/MockMvc tests prove application behavior; PostgreSQL integration tests prove migrations, constraints, races, cascades, and pgvector startup.

### Task 1: Typed FatSecret monthly response classification

**Files:**
- Create: `src/main/java/com/fit/fitnessapp/nutrition/domain/NutritionMonthFetchResult.java`
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/application/port/out/FatSecretApiPort.java`
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/adapter/out/api/FatSecretApiAdapter.java`
- Create: `src/test/java/com/fit/fitnessapp/nutrition/adapter/out/api/FatSecretApiAdapterMonthParsingTest.java`

**Interfaces:**
- Produces: `NutritionMonthFetchResult` with `Status { VALID, AUTHORITATIVE_EMPTY, PROVIDER_ERROR, MALFORMED }`, `NutritionMonth snapshot`, and `String errorCode`.
- Changes: `FatSecretApiPort.fetchAndParseFoodEntriesForCurrentMonth(...)` returns `NutritionMonthFetchResult`.

- [ ] **Step 1: Write parser fixture tests that fail against the current untyped parser**

Place the test in the adapter package and exercise a package-private `parseMonthResponse(String, Long)` method. Include these assertions:

```java
@ParameterizedTest
@MethodSource("authoritativeSnapshots")
void classifiesAuthoritativeSnapshots(String json, NutritionMonthFetchResult.Status status, int days) {
    NutritionMonthFetchResult result = adapter.parseMonthResponse(json, 42L);

    assertThat(result.status()).isEqualTo(status);
    assertThat(result.snapshot().days()).hasSize(days);
}

static Stream<Arguments> authoritativeSnapshots() {
    return Stream.of(
            arguments("""{"month":{"day":[{"date_int":20635,"calories":"500","protein":"30","fat":"10","carbohydrate":"50"}]}}""",
                    VALID, 1),
            arguments("""{"month":{"day":{"date_int":20635,"calories":"500","protein":"30","fat":"10","carbohydrate":"50"}}}""",
                    VALID, 1),
            arguments("""{"month":{"day":[]}}""", AUTHORITATIVE_EMPTY, 0));
}

@ParameterizedTest
@ValueSource(strings = {"not-json", "{}", "{\"month\":{}}", "{\"month\":{\"day\":true}}",
        "{\"month\":{\"day\":{\"date_int\":\"bad\"}}}"})
void classifiesMalformedPayloads(String json) {
    assertThat(adapter.parseMonthResponse(json, 42L).status()).isEqualTo(MALFORMED);
}

@Test
void classifiesProviderErrorWithoutTreatingItAsEmpty() {
    NutritionMonthFetchResult result = adapter.parseMonthResponse(
            """{"error":{"code":"13","message":"Invalid token"}}""", 42L);

    assertThat(result.status()).isEqualTo(PROVIDER_ERROR);
    assertThat(result.errorCode()).isEqualTo("13");
    assertThat(result.snapshot()).isNull();
}
```

- [ ] **Step 2: Run the parser test and verify RED**

Run: `mvn -Dtest=FatSecretApiAdapterMonthParsingTest test`

Expected: compilation failure because `NutritionMonthFetchResult` and `parseMonthResponse` do not exist.

- [ ] **Step 3: Add the minimal validated domain result**

```java
public record NutritionMonthFetchResult(Status status, NutritionMonth snapshot, String errorCode) {
    public enum Status { VALID, AUTHORITATIVE_EMPTY, PROVIDER_ERROR, MALFORMED }

    public NutritionMonthFetchResult {
        Objects.requireNonNull(status, "status");
        if ((status == Status.VALID || status == Status.AUTHORITATIVE_EMPTY) && snapshot == null) {
            throw new IllegalArgumentException("Authoritative result requires a snapshot");
        }
        if ((status == Status.PROVIDER_ERROR || status == Status.MALFORMED) && snapshot != null) {
            throw new IllegalArgumentException("Failure result cannot carry a snapshot");
        }
    }

    public static NutritionMonthFetchResult valid(NutritionMonth value) {
        return new NutritionMonthFetchResult(Status.VALID, value, null);
    }

    public static NutritionMonthFetchResult authoritativeEmpty(Long userId) {
        return new NutritionMonthFetchResult(Status.AUTHORITATIVE_EMPTY,
                new NutritionMonth(userId, List.of()), null);
    }

    public static NutritionMonthFetchResult providerError(String code) {
        return new NutritionMonthFetchResult(Status.PROVIDER_ERROR, null, code);
    }

    public static NutritionMonthFetchResult malformed() {
        return new NutritionMonthFetchResult(Status.MALFORMED, null, null);
    }
}
```

- [ ] **Step 4: Implement strict response classification and update the port**

Change the port signature:

```java
NutritionMonthFetchResult fetchAndParseFoodEntriesForCurrentMonth(
        FatSecretToken token, Long userId, long currentDaysInMonth);
```

In the adapter, return `parseMonthResponse(response.getBody(), userId)`. The package-private parser must first parse JSON, then check `root.get("error")`, require an object-valued `month`, require a present `day`, accept only array/object `day`, validate required scalar fields with `canConvertToInt`/`isNumber` or strict textual numeric parsing, and return `MALFORMED` from the catch block. Do not use `path(...).asInt()` defaults for required fields.

- [ ] **Step 5: Run the parser fixtures and nutrition unit suite**

Run: `mvn -Dtest=FatSecretApiAdapterMonthParsingTest,NutritionServiceSyncWorkflowTest test`

Expected: parser fixtures pass; `NutritionServiceSyncWorkflowTest` fails to compile until Task 2 adopts the typed result.

- [ ] **Step 6: Commit the isolated contract change after Task 2 restores compilation**

Commit message: `fix: classify FatSecret monthly snapshots`

### Task 2: Guard destructive nutrition reconciliation

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionService.java`
- Modify: `src/test/java/com/fit/fitnessapp/nutrition/NutritionServiceSyncWorkflowTest.java`

**Interfaces:**
- Consumes: `NutritionMonthFetchResult` from Task 1.
- Preserves: existing `SyncNutritionUseCase.syncMonth(Long)` signature.

- [ ] **Step 1: Update successful fixtures and add no-write/no-event tests**

Wrap existing month fixtures with `NutritionMonthFetchResult.valid(month)`. Add a parameterized failure test:

```java
@ParameterizedTest
@EnumSource(value = Status.class, names = {"PROVIDER_ERROR", "MALFORMED"})
void syncMonthDoesNotWriteOrPublishWhenSnapshotIsNotAuthoritative(Status status) {
    FatSecretToken token = new FatSecretToken("access", "secret");
    when(commandPort.getToken(42L)).thenReturn(Optional.of(token));
    when(apiPort.fetchAndParseFoodEntriesForCurrentMonth(eq(token), eq(42L), anyLong()))
            .thenReturn(status == Status.PROVIDER_ERROR
                    ? NutritionMonthFetchResult.providerError("13")
                    : NutritionMonthFetchResult.malformed());

    assertThatThrownBy(() -> service.syncMonth(42L)).isInstanceOf(ExternalApiException.class);
    verify(commandPort, never()).saveNutritionMonth(any());
    verify(commandPort, never()).deleteNutritionDaysMissingFromMonth(anyLong(), any(), any(), any());
    verify(commandPort, never()).saveNutritionDay(any());
    verifyNoInteractions(eventPublisher);
}
```

Add one `AUTHORITATIVE_EMPTY` test proving `saveNutritionMonth(emptySnapshot)` and deletion over the full calendar month occur, while daily fetch is never called.

- [ ] **Step 2: Run the service test and verify RED**

Run: `mvn -Dtest=NutritionServiceSyncWorkflowTest test`

Expected: failure because `syncMonth` still treats the result as `NutritionMonth`.

- [ ] **Step 3: Switch on status before the first write**

```java
NutritionMonthFetchResult fetchResult = apiPort.fetchAndParseFoodEntriesForCurrentMonth(
        token, userId, today.toEpochDay());
NutritionMonth nutritionMonth = switch (fetchResult.status()) {
    case VALID, AUTHORITATIVE_EMPTY -> fetchResult.snapshot();
    case PROVIDER_ERROR -> throw new ExternalApiException(
            "FatSecret monthly response contained provider error " + fetchResult.errorCode(), null);
    case MALFORMED -> throw new ExternalApiException("FatSecret monthly response was malformed", null);
};
```

Keep all persistence calls after this switch. For an empty month, the existing empty `monthDates` set naturally deletes the full month and produces no detail dates.

- [ ] **Step 4: Run focused and full unit gates**

Run: `mvn -Dtest=FatSecretApiAdapterMonthParsingTest,NutritionServiceSyncWorkflowTest test`

Expected: PASS.

Run: `mvn test`

Expected: PASS with no external calls.

- [ ] **Step 5: Commit Tasks 1 and 2**

```text
fix: classify FatSecret monthly snapshots
```

### Task 3: Canonical registration and race-safe conflicts

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/auth/application/service/RegisterService.java`
- Modify: `src/main/java/com/fit/fitnessapp/auth/adapter/out/persistence/UserPersistenceAdapter.java`
- Modify: `src/main/java/com/fit/fitnessapp/auth/adapter/out/persistence/entity/user/User.java`
- Modify: `src/main/java/com/fit/fitnessapp/exception/GlobalExceptionHandler.java`
- Create: `src/test/java/com/fit/fitnessapp/auth/RegisterServiceTest.java`
- Create: `src/test/java/com/fit/fitnessapp/auth/UserPersistenceAdapterTest.java`
- Modify: `src/test/java/com/fit/fitnessapp/auth/AuthControllerTest.java`

**Interfaces:**
- Preserves: `RegisterUserPort.registerUser(RegisterRequest)` and `UserPersistencePort.registerUser(RegisterRequest)`.
- Produces: persistence always receives trimmed username and `trim().toLowerCase(Locale.ROOT)` email.

- [ ] **Step 1: Write normalization and persistence-race tests**

```java
@Test
void normalizesIdentityBeforePersistence() {
    service.registerUser(new RegisterRequest("  John  ", "Secret123!", "  JOHN@Example.COM "));

    verify(persistence).registerUser(
            new RegisterRequest("John", "Secret123!", "john@example.com"));
}
```

For `UserPersistenceAdapterTest`, mock `userRepository.save` to throw `DataIntegrityViolationException` and assert `UserAlreadyExistsException`. Also capture a saved user and assert username/email lengths and canonical values are not changed again.

Change the MockMvc duplicate assertion to `status().isConflict()` and `$.status == 409`. Add validation cases for a 65-character username and 256-character email.

- [ ] **Step 2: Run auth tests and verify RED**

Run: `mvn -Dtest=RegisterServiceTest,UserPersistenceAdapterTest,AuthControllerTest test`

Expected: normalization and 409 assertions fail.

- [ ] **Step 3: Normalize once in `RegisterService`**

```java
@Override
public void registerUser(RegisterRequest request) {
    RegisterRequest canonical = new RegisterRequest(
            request.username().trim(),
            request.password(),
            request.email().trim().toLowerCase(Locale.ROOT));
    userPersistencePort.registerUser(canonical);
}
```

- [ ] **Step 4: Translate the database race and align entity metadata**

Wrap only `userRepository.saveAndFlush(user)` in a `try/catch (DataIntegrityViolationException)` and throw `UserAlreadyExistsException("Username or email already exists")`. Use `saveAndFlush` so the constraint violation occurs inside the adapter call.

Set entity columns exactly:

```java
@Column(nullable = false, unique = true, length = 64)
private String username;

@Column(nullable = false, unique = true, length = 255)
private String email;

@Column(nullable = false, length = 255)
private String password;
```

Change `GlobalExceptionHandler.userAlreadyExists` from `BAD_REQUEST` to `CONFLICT`.

- [ ] **Step 5: Run focused auth tests**

Run: `mvn -Dtest=RegisterServiceTest,UserPersistenceAdapterTest,AuthControllerTest test`

Expected: PASS.

- [ ] **Step 6: Commit registration application behavior**

Commit message: `fix: canonicalize registration identity`

### Task 4: Forward-only identity and ownership migration

**Files:**
- Create: `src/main/resources/db/migration/V14__enforce_identity_and_ownership.sql`
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/entity/FatsecretFoodEntry.java`
- Modify: `src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/entity/WorkoutJpaEntity.java`
- Modify: `src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/entity/WorkoutExerciseJpaEntity.java`
- Modify: `src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/entity/WorkoutSetJpaEntity.java`
- Create: `src/test/java/com/fit/fitnessapp/auth/IdentityAndOwnershipMigrationIntegrationTest.java`

**Interfaces:**
- Produces: unique canonical email plus non-null ownership/aggregate-child FKs with `ON DELETE CASCADE`.

- [ ] **Step 1: Write PostgreSQL tests for schema and runtime constraints**

Extend `AbstractPostgresIntegrationTest`, clean owned tables in child-first order, and add tests that:

```java
assertThat(columnLength("users", "username")).isEqualTo(64);
assertThat(columnLength("users", "email")).isEqualTo(255);
assertThatThrownBy(() -> jdbc.update(
        "INSERT INTO users(username,email,password) VALUES (?,?,?)",
        "second", "same@example.com", "{noop}x"))
        .hasRootCauseInstanceOf(SQLException.class);
```

Insert `fatsecret_day`, `workout`, and `workout_cardio` with an unknown `user_id` and assert rejection. Insert a user with full nutrition/workout/cardio children, delete the user, and assert zero rows remain in every aggregate table.

Add a migration-audit test using an isolated schema: migrate to version 13, insert colliding emails differing only by case/whitespace, then assert migration to 14 fails and preserves both rows.

- [ ] **Step 2: Run the integration test and verify RED**

Run: `mvn -Pintegration -Dit.test=IdentityAndOwnershipMigrationIntegrationTest verify`

Expected: FAIL because V14 and ownership constraints do not exist.

- [ ] **Step 3: Add V14 with explicit audit blocks before DDL**

Use PostgreSQL `DO $$ ... $$` blocks that raise exceptions when either query finds rows:

```sql
IF EXISTS (
    SELECT lower(btrim(email)) FROM users
    GROUP BY lower(btrim(email)) HAVING count(*) > 1
) THEN
    RAISE EXCEPTION 'Cannot enforce canonical email uniqueness: duplicate normalized emails exist';
END IF;
```

Run analogous null/orphan checks for `fatsecret_day.user_id`, `workout.user_id`, `workout_cardio.user_id`, `fatsecret_food.day_id`, `workout_exercises.workout_id`, and `workout_sets.exercise_id`.

Then execute, in this order:

```sql
UPDATE users SET email = lower(btrim(email));
ALTER TABLE users ALTER COLUMN username TYPE VARCHAR(64);
ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(255);
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);

ALTER TABLE fatsecret_day ADD CONSTRAINT fk_fatsecret_day_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE workout ADD CONSTRAINT fk_workout_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE workout_cardio ADD CONSTRAINT fk_workout_cardio_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE fatsecret_food ALTER COLUMN day_id SET NOT NULL;
ALTER TABLE workout_exercises ALTER COLUMN workout_id SET NOT NULL;
ALTER TABLE workout_sets ALTER COLUMN exercise_id SET NOT NULL;
```

The three ownership columns are already `NOT NULL`; repeat `SET NOT NULL` in V14 for an explicit invariant. Existing child FKs already cascade, so retain them rather than replacing equivalent constraints.

- [ ] **Step 4: Align JPA nullable metadata**

Set `nullable = false` on `FatsecretFoodEntry.day`, `WorkoutJpaEntity.userId`, `WorkoutExerciseJpaEntity.workoutJpaEntity`, and `WorkoutSetJpaEntity.exercise`. Keep cascade/orphan removal behavior unchanged.

- [ ] **Step 5: Run migration and existing persistence integration tests**

Run: `mvn -Pintegration -Dit.test=IdentityAndOwnershipMigrationIntegrationTest,NutritionPersistenceAdapterPostgresIntegrationTest,WorkoutPersistenceAdapterPostgresIntegrationTest verify`

Expected: PASS.

- [ ] **Step 6: Commit the schema contract**

Commit message: `fix: enforce user ownership in PostgreSQL`

### Task 5: Concurrent PostgreSQL registration proof

**Files:**
- Create: `src/test/java/com/fit/fitnessapp/auth/RegistrationConcurrencyIntegrationTest.java`

**Interfaces:**
- Consumes: `RegisterUserPort` and V14 canonical email constraint.
- Proves: two simultaneous inserts produce one success and one `UserAlreadyExistsException` with one stored row.

- [ ] **Step 1: Write the concurrent integration test**

Use two tasks, a `CountDownLatch ready = new CountDownLatch(2)`, and a `CountDownLatch start = new CountDownLatch(1)`. Each task calls `registerUserPort.registerUser` with distinct usernames but emails that normalize to the same value. Capture each result as either `SUCCESS` or the thrown class name.

```java
assertThat(results).containsExactlyInAnyOrder("SUCCESS", "UserAlreadyExistsException");
assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM users WHERE email = ?", Long.class, "race@example.com"))
        .isEqualTo(1L);
```

Use `Executors.newFixedThreadPool(2)` in try/finally and always `shutdownNow()`.

- [ ] **Step 2: Run the test and confirm it exercises the constraint path**

Run: `mvn -Pintegration -Dit.test=RegistrationConcurrencyIntegrationTest verify`

Expected: PASS only when `saveAndFlush` translates the losing transaction to `UserAlreadyExistsException`.

- [ ] **Step 3: Commit the concurrency proof**

Commit message: `test: prove registration uniqueness under race`

### Task 6: Dev pgvector parity and release gate

**Files:**
- Modify: `src/main/resources/application-dev.properties`
- Modify: `src/test/java/com/fit/fitnessapp/memory/MemoryPgVectorIntegrationTest.java`
- Modify: `src/test/java/com/fit/fitnessapp/CodeHygieneTest.java`
- Verify: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: dev properties `table-name=user_memory`, `index-type=NONE`, `dimension=2048`.
- Proves: clean Flyway/Testcontainers startup creates `vector(2048)` without an HNSW index.

- [ ] **Step 1: Add a failing dev-property parity test**

In `CodeHygieneTest`, load both `application.properties` and `application-dev.properties`, then assert:

```java
assertThat(devProperties)
        .containsEntry("spring.ai.vectorstore.pgvector.initialize-schema", "false")
        .containsEntry("spring.ai.vectorstore.pgvector.table-name", "user_memory")
        .containsEntry("spring.ai.vectorstore.pgvector.index-type", "NONE")
        .containsEntry("spring.ai.vectorstore.pgvector.dimension", "2048");
```

Also assert the dev file does not contain `vector_store` or `HNSW`.

- [ ] **Step 2: Run the property test and verify RED**

Run: `mvn -Dtest=CodeHygieneTest test`

Expected: FAIL on dev `vector_store` and `HNSW`.

- [ ] **Step 3: Align dev configuration**

```properties
spring.ai.vectorstore.pgvector.initialize-schema=false
spring.ai.vectorstore.pgvector.table-name=user_memory
spring.ai.vectorstore.pgvector.index-type=NONE
spring.ai.vectorstore.pgvector.dimension=2048
```

- [ ] **Step 4: Make the pgvector schema assertion an explicit startup test**

Rename `assertPgvectorSchemaMigrated()` to a dedicated `@Test` named `cleanFlywayStartupCreatesExactScanUserMemorySchema`; retain the SQL assertions for `vector(2048)` and zero `idx_user_memory_embedding` rows. The inherited `@SpringBootTest` context startup against the pgvector container is the smoke test; dynamic properties already disable external services.

- [ ] **Step 5: Run focused gates**

Run: `mvn -Dtest=CodeHygieneTest test`

Expected: PASS.

Run: `mvn -Pintegration -Dit.test=MemoryPgVectorIntegrationTest verify`

Expected: PASS with clean Flyway startup and exact-scan schema assertions.

- [ ] **Step 6: Confirm CI wiring instead of adding duplicate workflow logic**

Inspect `.github/workflows/ci.yml` and verify the blocking integration job runs exactly `mvn -B verify -Pintegration`. If unchanged, do not edit it.

- [ ] **Step 7: Commit dev/runtime parity**

Commit message: `fix: align dev pgvector schema`

### Task 7: Phase verification and evidence audit

**Files:**
- Modify only if needed: `docs/STABLE_BASE_BACKLOG.md` (mark items complete only if the project convention supports completion markers; otherwise leave the source backlog unchanged and report evidence).

**Interfaces:**
- Consumes every deliverable from Tasks 1–6.
- Produces evidence for every acceptance criterion in the phase design.

- [ ] **Step 1: Run formatting and stale-reference checks**

Run: `git diff --check`

Expected: no whitespace errors.

Run: `rg -n "vector_store|index-type=HNSW|HttpStatus.BAD_REQUEST.*USER_ALREADY_EXISTS" src/main src/test`

Expected: no dev/runtime mismatch and no 400 mapping for duplicate registration.

- [ ] **Step 2: Run the fast default gate**

Run: `mvn test`

Expected: BUILD SUCCESS; all unit/MockMvc tests pass and no real provider calls occur.

- [ ] **Step 3: Run the Modulith gate**

Run: `mvn test -Parchitecture`

Expected: BUILD SUCCESS; no new module boundary violation.

- [ ] **Step 4: Run the complete PostgreSQL gate**

Run: `mvn verify -Pintegration`

Expected: BUILD SUCCESS; all integration tests, including clean Flyway, migration audit, ownership, concurrency, and pgvector tests, pass.

- [ ] **Step 5: Rebuild Graphify after code modifications**

Run: `npx graphify hook-rebuild`

Expected: graph rebuild completes. If the command is unavailable, report that fact without manually editing `.graphify` files.

- [ ] **Step 6: Audit acceptance criteria against authoritative evidence**

Create a completion table in the handoff with one row per `FND-001`, `FND-002`, `FND-003`, `FND-005`, and `FND-013`, naming the exact test(s), migration/config file, and final gate output that proves it. Any missing or indirect evidence keeps the phase open.

- [ ] **Step 7: Commit only any final corrective changes**

Use a message describing the correction; do not create an empty verification commit.
