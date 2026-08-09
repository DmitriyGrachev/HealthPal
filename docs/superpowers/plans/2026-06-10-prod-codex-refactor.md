# PROD_CODEX_REFACTOR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize the project with TDD, remove critical runtime/test blockers, and leave the codebase in a cleaner production-oriented state.

**Architecture:** Work in small red-green-refactor cycles. Start with tests that currently fail or are empty, then fix root causes in web slices, security, persistence naming, migration/test profile compatibility, and Telegram/analytics workflow bugs. Preserve the existing modular/ports-and-adapters shape.

**Tech Stack:** Java 21, Spring Boot 3.4.2, Maven, JUnit 5, Mockito, Spring MVC Test, Spring Security Test, Flyway, PostgreSQL/H2 test profile where appropriate.

---

## Current Baseline

- Branch requested by user: `PROD_CODEX_REFACTOR`.
- `mvn -q -DskipTests compile` passes.
- `mvn -q test` fails on:
  - `AuthControllerTest`: web slice tries to instantiate `RateLimitInterceptor` without `RateLimiterService`.
  - `FitnessAppApplicationTests`: Flyway migration uses PostgreSQL types unsupported by H2 (`TIMESTAMPTZ`).
- Critical code issues already identified:

## Progress Update - 2026-06-10

- Branch `PROD_CODEX_REFACTOR` exists and is active.
- `mvn -q test` passes.
- Modulith `verifyArchitecture()` is enabled and passes.
- Spring Data JDBC repository scanning noise is removed while keeping JDBC templates.
- Telegram and AI cross-module events were moved to the shared `com.fit.fitnessapp.api` package.
- Nutrition no longer depends directly on auth's internal `UserRepository`; it uses `auth.api.UserPort`.
- Production `System.out/err.println`, broad `.gitignore` adapter hiding, AI prompt dumps, duplicate AI fallback, workout table-name mismatch, security role mismatch, and broken Telegram note type handling were addressed through tests and minimal code changes.
  - `.gitignore` ignores every `out/` directory, including `src/main/java/**/adapter/out`.
  - `WorkoutJdbcQueryAdapter` queries `workouts`, while migration/entity use `workout`.
  - `UserRepository` declares `JpaRepository<User, User>` instead of `JpaRepository<User, Long>`.
  - Spring Security uses `hasRole(...)` while `User#getAuthorities()` returns role names without `ROLE_`.
  - Telegram note buttons emit values that do not match `UserNoteDto.NoteType`.
  - AI orchestrator logs full prompts and performs duplicate fallback retry.

---

### Task 1: Stabilize Git Visibility and Auth Web Slice

**Files:**
- Modify: `.gitignore`
- Modify: `src/test/java/com/fit/fitnessapp/auth/AuthControllerTest.java`
- Modify if test requires it: `src/main/java/com/fit/fitnessapp/auth/adapter/in/web/AuthController.java`

- [ ] **Step 1: Write failing auth controller tests**

Replace the empty test with tests that prove:
- `POST /auth/login` returns HTTP 200 and the token body from `LoginService`.
- `POST /auth/login` returns HTTP 401 for `BadCredentialsException`.
- `POST /auth/register` returns HTTP 200 and calls `RegisterUserPort`.
- `POST /auth/register` returns HTTP 400 for `UserAlreadyExistsException`.

- [ ] **Step 2: Run red test**

Run: `mvn -q -Dtest=com.fit.fitnessapp.auth.AuthControllerTest test`

Expected RED: context load fails because the web slice imports MVC config/rate limiter dependencies or because security rejects the request.

- [ ] **Step 3: Apply minimal test-slice fix**

Use test annotations/mocks appropriate for the web slice:
- Disable filters with `@AutoConfigureMockMvc(addFilters = false)` for controller behavior tests.
- Mock `RateLimiterService`, `RateLimitInterceptor`, or exclude MVC config only if required by the exact failure.

- [ ] **Step 4: Run green test**

Run: `mvn -q -Dtest=com.fit.fitnessapp.auth.AuthControllerTest test`

Expected GREEN: auth controller tests pass.

- [ ] **Step 5: Fix `.gitignore` visibility**

Change root build ignores from broad `out/` to root-only `/out/`, `/build/`, `/target/` so Java packages named `out` are visible to Git.

- [ ] **Step 6: Verify compile still passes**

Run: `mvn -q -DskipTests compile`

Expected GREEN: compile passes.

---

### Task 2: Fix Repository ID Type and Security Roles

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/auth/adapter/out/persistence/repository/UserRepository.java`
- Modify: `src/main/java/com/fit/fitnessapp/auth/adapter/out/persistence/entity/user/User.java`
- Modify or add tests: `src/test/java/com/fit/fitnessapp/auth/UserRepositoryContractTest.java` or focused unit tests for `User#getAuthorities()`

- [ ] **Step 1: Write failing authority test**

Add a test proving a user with `Role.VIP` exposes `ROLE_VIP`, and `Role.USER` exposes `ROLE_USER`.

- [ ] **Step 2: Run red test**

Run the focused test. Expected RED: authorities currently contain `VIP`/`USER`.

- [ ] **Step 3: Fix authorities minimally**

Map roles to `new SimpleGrantedAuthority("ROLE_" + role.name())`.

- [ ] **Step 4: Run green test**

Run focused authority test. Expected GREEN.

- [ ] **Step 5: Fix repository ID type**

Change `UserRepository extends JpaRepository<User, User>` to `JpaRepository<User, Long>` and remove redundant `findById(Long userId)` declaration.

- [ ] **Step 6: Verify compile**

Run: `mvn -q -DskipTests compile`

Expected GREEN.

---

### Task 3: Fix Workout Query Table Name and Re-enable Focused Query Coverage

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/workout/adapter/out/WorkoutJdbcQueryAdapter.java`
- Modify or replace: `src/test/java/com/fit/fitnessapp/jdbc/workoout/WorkoutQueryUseCaseTest.java`

- [ ] **Step 1: Write a focused failing test for SQL table names**

Add a unit test around `WorkoutJdbcQueryAdapter` with a mocked `NamedParameterJdbcTemplate` that captures SQL and asserts it references `FROM workout w` / `from workout as w`, not `workouts`.

- [ ] **Step 2: Run red test**

Run: `mvn -q -Dtest=com.fit.fitnessapp.jdbc.workoout.WorkoutQueryUseCaseTest test`

Expected RED: captured SQL contains `workouts`.

- [ ] **Step 3: Fix table references**

Replace `workouts` with `workout` in every query in `WorkoutJdbcQueryAdapter`.

- [ ] **Step 4: Run green test**

Run focused workout query test. Expected GREEN.

---

### Task 4: Make Context Test Honest

**Files:**
- Restore or create: `src/test/java/com/fit/fitnessapp/FitnessAppApplicationTests.java`
- Add if needed: `src/test/resources/application-test.properties`

- [ ] **Step 1: Write/restore context test**

Create a test using `@SpringBootTest` and `@ActiveProfiles("test")`.

- [ ] **Step 2: Run red test**

Run: `mvn -q -Dtest=com.fit.fitnessapp.FitnessAppApplicationTests test`

Expected RED: H2/Flyway cannot process PostgreSQL-specific migrations, or required external AI/Telegram/FatSecret beans are missing.

- [ ] **Step 3: Choose the smallest honest test profile**

Either:
- Disable Flyway in `application-test.properties` and use this as a bean-wiring smoke test, or
- Move database migration verification to PostgreSQL/Testcontainers later.

For this pass, prefer a fast context smoke test that does not pretend H2 validates PostgreSQL DDL.

- [ ] **Step 4: Run green context test**

Run focused context test. Expected GREEN.

---

### Task 5: Fix Telegram Note Type Workflow

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/NoteCommandHandler.java`
- Modify or add test: `src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/NoteCommandHandlerTest.java`

- [ ] **Step 1: Write failing test**

Test that selecting a visible keyboard option emits a `TelegramNoteRequestedEvent` whose `type` is accepted by `UserNoteDto.NoteType.valueOf`.

- [ ] **Step 2: Run red test**

Expected RED: `Training`, `Nutrition`, `General`, or `Mood` are not valid note types.

- [ ] **Step 3: Fix visible note options**

Use enum-compatible labels such as `ILLNESS`, `TRAVEL`, `INJURY`, `STRESS`, `ALLERGY`, `GOAL`, `PREFERENCE`, `OTHER`, or add a deliberate mapping function with tests.

- [ ] **Step 4: Run green test**

Expected GREEN.

---

### Task 6: Clean AI Runtime Hazards

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/ai/MoeOrchestrator.java`
- Modify: `src/test/java/com/fit/fitnessapp/ai/MoeOrchestratorTest.java`

- [ ] **Step 1: Write failing tests**

Prove:
- weekly/monthly routing does not call `SmartAiRouter` twice for the same failure.
- prompt text is not logged at INFO level.

- [ ] **Step 2: Run red tests**

Run: `mvn -q -Dtest=com.fit.fitnessapp.ai.MoeOrchestratorTest test`

Expected RED for duplicate retry and prompt logging behavior.

- [ ] **Step 3: Fix orchestrator**

Remove duplicate fallback call and lower/redact prompt logging.

- [ ] **Step 4: Run green tests**

Expected GREEN.

---

### Task 7: Full Verification Gate

**Files:**
- No new files expected.

- [ ] **Step 1: Run compile**

Run: `mvn -q -DskipTests compile`

Expected GREEN.

- [ ] **Step 2: Run all tests**

Run: `mvn -q test`

Expected GREEN or only explicitly documented remaining integration gaps.

- [ ] **Step 3: Inspect disabled tests**

Run: `rg --no-ignore -n "@Disabled|TODO|System.out.println|catch \\(Exception ignored\\)" src/main/java src/test/java`

Expected: no critical hidden test gaps or production debug prints remain for touched areas.

- [ ] **Step 4: Summarize remaining non-critical work**

List remaining backlog items that are not blocking compile/tests.
