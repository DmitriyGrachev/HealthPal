# AI Workflow Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize the nutrition/workout ingestion to AI insight workflow so daily insights are retryable, workout-aware, stale-safe, idempotent, async-friendly, memory-safe, and aware of Jefit cardio data.

**Architecture:** Keep module boundaries through public API events and existing ports. Prefer focused service methods and DTOs over broad refactors; use unit tests for orchestration, MockMvc for API status shape, PostgreSQL integration only for schema/persistence behavior when Docker is available.

**Tech Stack:** Java 21, Spring Boot, Spring Modulith, Maven, JUnit 5, AssertJ, Mockito, MockMvc, PostgreSQL/Flyway.

---

### Task 1: Daily Insight Retry And Stale Invalidation

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightService.java`
- Modify: `src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java`
- Modify: `src/main/java/com/fit/fitnessapp/ai/AiController.java`
- Test: `src/test/java/com/fit/fitnessapp/ai/application/service/DailyInsightServiceTest.java`
- Test: `src/test/java/com/fit/fitnessapp/ai/FitnessAiServiceDelegationTest.java`
- Test: `src/test/java/com/fit/fitnessapp/ai/AiControllerTest.java`

- [ ] Add failing tests that `generateOrPublishExisting` deletes or marks stale existing daily insight when the current snapshot is unavailable.
- [ ] Add failing tests that manual generation returns/propagates a `DailyInsightResult` status instead of hiding `NO_SNAPSHOT` or `AI_FAILED`.
- [ ] Implement minimal service return-type changes and stale invalidation.
- [ ] Run narrow AI tests.

### Task 2: Workout-Only Daily Snapshots

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightSnapshot.java`
- Modify: `src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightSnapshotService.java`
- Modify: `src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightService.java`
- Modify: `src/main/resources/ai/prompts/daily-insight-v1.md`
- Test: `src/test/java/com/fit/fitnessapp/ai/application/service/DailyInsightSnapshotServiceTest.java`
- Test: `src/test/java/com/fit/fitnessapp/ai/application/service/DailyInsightServiceTest.java`
- Test: `src/test/java/com/fit/fitnessapp/ai/AiPromptRendererTest.java`

- [ ] Add failing tests for workout-only snapshots when nutrition has no entries but workout stats exist.
- [ ] Add failing tests that snapshot metadata includes source coverage.
- [ ] Implement workout-only snapshot support and prompt variables.
- [ ] Run narrow AI/prompt tests.

### Task 3: Workout Import Changed Dates

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/workout/application/port/out/WorkoutPersistencePort.java`
- Create: `src/main/java/com/fit/fitnessapp/workout/domain/WorkoutPersistenceResult.java`
- Modify: `src/main/java/com/fit/fitnessapp/workout/application/service/WorkoutImportService.java`
- Modify: `src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/WorkoutPersistenceAdapter.java`
- Test: `src/test/java/com/fit/fitnessapp/workout/WorkoutImportServiceTest.java`
- Test: `src/test/java/com/fit/fitnessapp/workout/WorkoutPersistenceAdapterTest.java`

- [ ] Add failing test that no `WorkoutImportedEvent` is published when persistence reports no changed dates.
- [ ] Add failing test that persistence reports changed dates only for newly created or materially changed sessions.
- [ ] Implement `WorkoutPersistenceResult` and changed-date detection.
- [ ] Run narrow workout tests.

### Task 4: Async-Friendly AI Request Status

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/ai/AiController.java`
- Modify: `src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java`
- Test: `src/test/java/com/fit/fitnessapp/ai/AiControllerTest.java`
- Test: `src/test/java/com/fit/fitnessapp/ai/FitnessAiServiceDelegationTest.java`

- [ ] Add failing MockMvc test that generate endpoint response reflects `GENERATED`, `SKIPPED_FRESH`, `NO_SNAPSHOT`, or `AI_FAILED`.
- [ ] Add failing delegation test that `/today` handles null defensively with fallback response.
- [ ] Implement minimal response mapping without adding external queues.
- [ ] Run narrow API/AI tests.

### Task 5: Memory Insight Upsert

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/api/InsightGeneratedEvent.java`
- Modify: `src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightService.java`
- Modify: `src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java`
- Modify: `src/main/java/com/fit/fitnessapp/memory/application/service/MemoryEventListener.java`
- Test: `src/test/java/com/fit/fitnessapp/memory/MemoryServiceTest.java` or create focused listener test
- Test: AI event tests that construct `InsightGeneratedEvent`

- [ ] Add failing test that memory documents include deterministic insight memory id.
- [ ] Add failing test that regenerated insights delete the previous vector document before adding the new one.
- [ ] Add snapshot hash to `InsightGeneratedEvent` and use vector store delete/add semantics.
- [ ] Run narrow memory and AI event tests.

### Task 6: Jefit Cardio Parsing And Domain Storage

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/workout/domain/WorkoutSession.java`
- Create: `src/main/java/com/fit/fitnessapp/workout/domain/CardioExercise.java`
- Modify: `src/main/java/com/fit/fitnessapp/workout/adapter/out/parser/JefitCsvParserAdapter.java`
- Modify: workout JPA entities/repositories if persistence needs cardio storage
- Add Flyway migration if schema changes are needed
- Test: `src/test/java/com/fit/fitnessapp/workout/JefitCsvParserAdapterTest.java`
- Test: workout persistence tests

- [ ] Add failing parser test for `CARDIO LOGS` and `CARDIO EXERCISE LOGS`.
- [ ] Implement parser domain support.
- [ ] Persist cardio data only if needed for AI stats; otherwise include warnings and parsed domain data first.
- [ ] Run narrow workout tests.

### Task 7: Cardio/Workout Context In Daily Insight

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/workout/WorkoutDailyStatsDto.java`
- Modify: `src/main/java/com/fit/fitnessapp/workout/adapter/out/WorkoutJdbcQueryAdapter.java`
- Modify: `src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightSnapshot.java`
- Modify: `src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightSnapshotService.java`
- Modify: `src/main/resources/ai/prompts/daily-insight-v1.md`
- Test: `src/test/java/com/fit/fitnessapp/ai/application/service/DailyInsightSnapshotServiceTest.java`
- Test: `src/test/java/com/fit/fitnessapp/jdbc/workout/WorkoutJdbcQueryAdapterSqlTest.java`

- [ ] Add failing tests that daily insight snapshot includes cardio minutes/calories when available.
- [ ] Add SQL/query mapping tests for new cardio fields.
- [ ] Implement context propagation into prompt and snapshot hash.
- [ ] Run narrow AI/workout tests, then `mvn test` and `mvn test -Parchitecture`.
