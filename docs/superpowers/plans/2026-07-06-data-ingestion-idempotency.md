# Data Ingestion Idempotency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make nutrition and workout ingestion idempotent, cheaper to re-run, and reliable as inputs to Daily Insight, Weekly Report, Monthly Report, Memory, and Telegram notification workflows.

**Architecture:** Treat imported data as source snapshots with explicit hashes and change results. Workout import should replace the imported Jefit session shape without duplicating sets. Nutrition sync should separate month-summary freshness from full-day-entry freshness so `syncMonth` does not churn all days every time.

**Tech Stack:** Java 21, Spring Boot, Spring Modulith events, JPA repositories, JDBC query adapters, Flyway PostgreSQL migrations, JUnit 5, AssertJ, Mockito, Testcontainers PostgreSQL for integration gates when Docker is available.

---

## Current Findings

- `WorkoutPersistenceAdapter.saveAll` builds `existingExercises` keyed by `jefitLogId`, then calls `setJpaRepository.deleteAllByExerciseIdIn(existingExercises.keySet())`. The repository query deletes by DB `exercise.id`, so repeated import can miss old sets and append duplicates.
- `WorkoutPersistenceAdapter.saveAll` updates incoming exercises but does not remove exercises that disappeared from the imported session. `WorkoutJpaEntity.exercises` has `orphanRemoval = true`, so this can be fixed locally.
- The attached Jefit export is a sectioned CSV, not a normal single-table CSV. It contains 21 `WORKOUT SESSIONS`, 89 `EXERCISE LOGS`, and 248 `EXERCISE SET LOGS`. Current parser structure matches this format; do not commit the private CSV.
- `NutritionPersistenceAdapter.computeHashForDay` ignores protein, fat, carbohydrate, and external food id. A macro-only edit with unchanged calories/name/meal could be skipped.
- `NutritionPersistenceAdapter.saveNutritionMonth` writes a summary hash to `external_hash`, then `syncMonth` fetches full days and `saveNutritionDay` writes a different entry hash to the same column. On the next month sync, summary hash and entry hash differ again, so month sync is not truly idempotent.
- `NutritionService.syncMonth` fetches full entries for every day returned by `food_entries.get_month.v2`. It should fetch details only for changed days plus a small recent safety window.
- `NutritionService.syncDay` publishes `NutritionSyncedEvent` even when persistence skipped an unchanged day. Daily AI currently skips if the insight exists, but the event noise hides real freshness semantics.
- Official FatSecret docs still allow method-based `server.api` calls, but recommend URL-based integrations for new work. `food_entries.get_month.v2` returns only days with entries, and FatSecret exercise entries are daily activity/calorie entries rather than strength-training set logs.

## File Map

- Modify: `src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/WorkoutPersistenceAdapter.java`
  - Fix set deletion to use DB exercise ids.
  - Remove stale exercises from an existing workout when they are absent from incoming Jefit data.
  - Keep replacement behavior local to persistence.
- Modify: `src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/entity/WorkoutExerciseJpaEntity.java`
  - Add `orphanRemoval = true` to `sets`, or keep explicit bulk delete plus local collection clearing. Prefer both explicit delete and clean in-memory collection.
- Create: `src/test/java/com/fit/fitnessapp/workout/WorkoutPersistenceAdapterTest.java`
  - Unit-test delete ids and stale exercise removal with mocked repositories.
- Modify: `src/test/java/com/fit/fitnessapp/workout/JefitCsvParserAdapterTest.java`
  - Add a synthetic fixture with the same section headers as the attached export.
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/entity/FatsecretJpaDay.java`
  - Add `summaryHash` and `entriesHash` fields.
- Create: `src/main/resources/db/migration/V11__split_nutrition_sync_hashes.sql`
  - Add `summary_hash` and `entries_hash` columns while preserving existing `external_hash`.
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/application/port/out/NutritionCommandPort.java`
  - Return save results instead of `void` for day/month saves.
- Create: `src/main/java/com/fit/fitnessapp/nutrition/domain/NutritionDaySaveResult.java`
  - `record NutritionDaySaveResult(Long userId, LocalDate date, boolean changed, String summaryHash, String entriesHash, int totalCalories, double protein, double fat, double carbohydrate)`.
- Create: `src/main/java/com/fit/fitnessapp/nutrition/domain/NutritionMonthSaveResult.java`
  - `record NutritionMonthSaveResult(Long userId, List<LocalDate> changedDates)`.
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/NutritionPersistenceAdapter.java`
  - Use deterministic summary and entry hashes.
  - Avoid full rewrites when summaries or entries are unchanged.
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionService.java`
  - Publish `NutritionSyncedEvent` only for changed day snapshots, or include `changed=false` only when user-triggered flows require visibility.
  - Fetch full month day details only for changed dates plus recent safety window.
- Modify: `src/main/java/com/fit/fitnessapp/api/NutritionSyncedEvent.java`
  - Add source hash and changed metadata if downstream staleness needs it.
- Create: `src/test/java/com/fit/fitnessapp/nutrition/NutritionPersistenceAdapterIdempotencyTest.java`
  - Mock repositories for fast idempotency tests.
- Modify: `src/test/java/com/fit/fitnessapp/nutrition/NutritionServicePrivacyLoggingTest.java`
  - Extend or add a sibling test for event-publishing semantics without logging private food details.
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionSyncScheduler.java`
  - Add sliding-window sync after day/month save results exist.
- Modify: `src/test/java/com/fit/fitnessapp/nutrition/application/service/NutritionSyncSchedulerTest.java`
  - Prove today/yesterday or configured window behavior.
- Later modify: `src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightService.java`
  - Consume a daily snapshot and regenerate stale insights only after ingestion freshness is reliable.

---

## Iteration 1: Prove Jefit Format And Fix Workout Reimport Idempotency

**Files:**
- Modify: `src/test/java/com/fit/fitnessapp/workout/JefitCsvParserAdapterTest.java`
- Create: `src/test/java/com/fit/fitnessapp/workout/WorkoutPersistenceAdapterTest.java`
- Modify: `src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/WorkoutPersistenceAdapter.java`
- Modify: `src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/entity/WorkoutExerciseJpaEntity.java`

- [ ] **Step 1: Add a synthetic Jefit section fixture test**

Add this test to `JefitCsvParserAdapterTest`. It mirrors the attached export structure but contains no private data.

```java
@Test
void parsesCurrentJefitSectionedExportShape() {
    WorkoutImportResult result = parser.parse(csv("""
            ### WORKOUT SESSIONS
            rowid,_id,USERID,edit_time,day_id,total_time,workout_time,rest_time,wasted_time,total_exercises,starttime,endtime
            1,1770220318,7,0,0,0,0,0,0,2,1770220318,1770223918
            ### EXERCISE LOGS
            USERID,TIMESTAMP,belongSys,logs,_id,record,mydate,eid,ename,day_item_id,belongsession,logTime,interval_logs,auto_generated
            7,0,0,"64.9998x5,64.9998x5",1,75.83,2026-02-04,8,Barbell Incline Bench Press,8,1770220318,0,,0
            7,0,0,"31.9998x8,31.9998x8",2,40.53,2026-02-04,45,Machine Fly,9,1770220318,0,,0
            ### EXERCISE SET LOGS
            _id,userid,exercise_log_id,set_index,weight_lbs,reps,calories,distance_mi,speed_mph,laps,duration
            1,7,1,0,143.2999,5,0,0,0,0,0
            2,7,1,1,143.2999,5,0,0,0,0,0
            3,7,2,0,70.5479,8,0,0,0,0,0
            4,7,2,1,70.5479,8,0,0,0,0,0
            """));

    assertThat(result.warnings()).isEmpty();
    assertThat(result.sessions()).hasSize(1);
    assertThat(result.sessions().getFirst().externalId()).isEqualTo(1770220318L);
    assertThat(result.sessions().getFirst().exercises()).hasSize(2);
    assertThat(result.sessions().getFirst().exercises().getFirst().sets()).hasSize(2);
}
```

- [ ] **Step 2: Run the parser test and verify baseline**

Run:

```bash
mvn "-Dtest=JefitCsvParserAdapterTest" test
```

Expected: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 3: Add a unit test for deleting sets by DB exercise ids**

Create `WorkoutPersistenceAdapterTest` with mocked repositories. Use reflection helpers or package-visible setters already available on entities through Lombok.

```java
@ExtendWith(MockitoExtension.class)
class WorkoutPersistenceAdapterTest {

    @Mock WorkoutJpaRepository workoutRepository;
    @Mock WorkoutExerciseJpaRepository exerciseRepository;
    @Mock WorkoutSetJpaRepository setRepository;
    @Mock CurrentUserApi currentUserApi;

    @Test
    void reimportDeletesSetsByExerciseDatabaseIdsNotJefitLogIds() {
        WorkoutJpaEntity existingWorkout = workoutEntity(10L, 1770220318L, 42L);
        WorkoutExerciseJpaEntity existingExercise = exerciseEntity(100L, 1L, existingWorkout);
        existingWorkout.getExercises().add(existingExercise);

        when(workoutRepository.findWithExercisesByJefitIdInAndUserId(List.of(1770220318L), 42L))
                .thenReturn(List.of(existingWorkout));

        WorkoutPersistenceAdapter adapter = new WorkoutPersistenceAdapter(
                workoutRepository,
                exerciseRepository,
                setRepository,
                currentUserApi);

        adapter.saveAll(List.of(new WorkoutSession(
                1770220318L,
                LocalDateTime.of(2026, 2, 4, 18, 0),
                List.of(new Exercise(1L, "Bench Press", List.of(new Set(0, 5, 65.0)))))
        ), 42L);

        verify(setRepository).deleteAllByExerciseIdIn(List.of(100L));
        verify(setRepository, never()).deleteAllByExerciseIdIn(List.of(1L));
        verify(workoutRepository).saveAll(List.of(existingWorkout));
    }
}
```

Add private helpers in the test:

```java
private WorkoutJpaEntity workoutEntity(Long id, Long jefitId, Long userId) {
    WorkoutJpaEntity entity = new WorkoutJpaEntity();
    ReflectionTestUtils.setField(entity, "id", id);
    entity.setJefitId(jefitId);
    entity.setUserId(userId);
    entity.setDate(LocalDateTime.of(2026, 2, 4, 18, 0));
    return entity;
}

private WorkoutExerciseJpaEntity exerciseEntity(Long id, Long jefitLogId, WorkoutJpaEntity workout) {
    WorkoutExerciseJpaEntity entity = new WorkoutExerciseJpaEntity();
    ReflectionTestUtils.setField(entity, "id", id);
    entity.setJefitLogId(jefitLogId);
    entity.setExerciseName("Old Bench Press");
    entity.setWorkoutJpaEntity(workout);
    return entity;
}
```

- [ ] **Step 4: Run the failing unit test**

Run:

```bash
mvn "-Dtest=WorkoutPersistenceAdapterTest" test
```

Expected before implementation: failure showing `deleteAllByExerciseIdIn([1])` instead of `[100]`.

- [ ] **Step 5: Fix `WorkoutPersistenceAdapter`**

Change the delete id collection:

```java
List<Long> existingExerciseDbIds = existingExercises.values().stream()
        .map(WorkoutExerciseJpaEntity::getId)
        .filter(Objects::nonNull)
        .toList();

if (!existingExerciseDbIds.isEmpty()) {
    setJpaRepository.deleteAllByExerciseIdIn(existingExerciseDbIds);
    existingExercises.values().forEach(ex -> ex.getSets().clear());
}
```

Add `import java.util.Objects;`.

- [ ] **Step 6: Remove stale exercises absent from incoming session**

Before adding incoming exercises for a session, compute incoming log ids and prune the existing workout:

```java
Set<Long> incomingExerciseLogIds = session.exercises().stream()
        .map(Exercise::jefitLogId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

workout.getExercises().removeIf(existing ->
        existing.getJefitLogId() != null
                && !incomingExerciseLogIds.contains(existing.getJefitLogId()));
```

Use `java.util.Set` and keep the domain `com.fit.fitnessapp.workout.domain.Set` import explicit to avoid name collision.

- [ ] **Step 7: Enable set orphan cleanup**

In `WorkoutExerciseJpaEntity`, change:

```java
@OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL)
private List<WorkoutSetJpaEntity> sets = new ArrayList<>();
```

to:

```java
@OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
private List<WorkoutSetJpaEntity> sets = new ArrayList<>();
```

- [ ] **Step 8: Run narrow workout tests**

Run:

```bash
mvn "-Dtest=JefitCsvParserAdapterTest,WorkoutImportServiceTest,WorkoutPersistenceAdapterTest" test
```

Expected: all pass.

- [ ] **Step 9: Commit iteration 1**

```bash
git add src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/WorkoutPersistenceAdapter.java \
        src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/entity/WorkoutExerciseJpaEntity.java \
        src/test/java/com/fit/fitnessapp/workout/JefitCsvParserAdapterTest.java \
        src/test/java/com/fit/fitnessapp/workout/WorkoutPersistenceAdapterTest.java
git commit -m "fix: make workout import idempotent"
```

---

## Iteration 2: Split Nutrition Summary Hashes From Entry Hashes

**Files:**
- Create: `src/main/resources/db/migration/V11__split_nutrition_sync_hashes.sql`
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/entity/FatsecretJpaDay.java`
- Create: `src/main/java/com/fit/fitnessapp/nutrition/domain/NutritionDaySaveResult.java`
- Create: `src/main/java/com/fit/fitnessapp/nutrition/domain/NutritionMonthSaveResult.java`
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/application/port/out/NutritionCommandPort.java`
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/NutritionPersistenceAdapter.java`
- Create: `src/test/java/com/fit/fitnessapp/nutrition/NutritionPersistenceAdapterIdempotencyTest.java`

- [ ] **Step 1: Add Flyway migration**

Create:

```sql
ALTER TABLE fatsecret_day
    ADD COLUMN IF NOT EXISTS summary_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS entries_hash VARCHAR(64);

UPDATE fatsecret_day
SET summary_hash = COALESCE(summary_hash, external_hash),
    entries_hash = COALESCE(entries_hash, external_hash)
WHERE external_hash IS NOT NULL;
```

- [ ] **Step 2: Add entity fields**

In `FatsecretJpaDay` add:

```java
@Column(name = "summary_hash")
private String summaryHash;

@Column(name = "entries_hash")
private String entriesHash;
```

- [ ] **Step 3: Add save result records**

Create `NutritionDaySaveResult`:

```java
package com.fit.fitnessapp.nutrition.domain;

import java.time.LocalDate;

public record NutritionDaySaveResult(
        Long userId,
        LocalDate date,
        boolean changed,
        String summaryHash,
        String entriesHash,
        int totalCalories,
        double protein,
        double fat,
        double carbohydrate
) {}
```

Create `NutritionMonthSaveResult`:

```java
package com.fit.fitnessapp.nutrition.domain;

import java.time.LocalDate;
import java.util.List;

public record NutritionMonthSaveResult(
        Long userId,
        List<LocalDate> changedDates
) {}
```

- [ ] **Step 4: Change command port return types**

Change:

```java
void saveNutritionDay(NutritionDay nutritionDay);
void saveNutritionMonth(NutritionMonth nutritionMonth);
```

to:

```java
NutritionDaySaveResult saveNutritionDay(NutritionDay nutritionDay);
NutritionMonthSaveResult saveNutritionMonth(NutritionMonth nutritionMonth);
```

- [ ] **Step 5: Write failing hash tests**

In `NutritionPersistenceAdapterIdempotencyTest`, test that macro changes change the day entries hash:

```java
@Test
void dayHashIncludesMacrosAndFoodIdentity() {
    NutritionDay first = dayWithEntry(42L, LocalDate.of(2026, 7, 6), 1L, 10L, 500, 30.0, 10.0, 50.0);
    NutritionDay changedProtein = dayWithEntry(42L, LocalDate.of(2026, 7, 6), 1L, 10L, 500, 35.0, 10.0, 50.0);

    NutritionDaySaveResult firstResult = adapter.saveNutritionDay(first);
    NutritionDaySaveResult changedResult = adapter.saveNutritionDay(changedProtein);

    assertThat(firstResult.entriesHash()).isNotEqualTo(changedResult.entriesHash());
}
```

Use mocked repositories and captors to return an existing `FatsecretJpaDay` between calls.

- [ ] **Step 6: Write failing month no-churn test**

Test that after full-day save sets both hashes, month summary save sees no change for same totals:

```java
@Test
void monthSummaryDoesNotChurnAfterFullDaySaveWithSameTotals() {
    LocalDate date = LocalDate.of(2026, 7, 6);
    NutritionDaySaveResult dayResult = adapter.saveNutritionDay(
            dayWithEntry(42L, date, 1L, 10L, 500, 30.0, 10.0, 50.0));

    FatsecretJpaDay existing = capturedSavedDay();
    when(dayRepository.findByUserIdAndDateIn(42L, List.of(date))).thenReturn(List.of(existing));

    NutritionMonthSaveResult monthResult = adapter.saveNutritionMonth(new NutritionMonth(
            42L,
            List.of(new NutritionDaySummary(42L, date, (int) date.toEpochDay(), 500, 30.0, 10.0, 50.0))));

    assertThat(monthResult.changedDates()).isEmpty();
    assertThat(existing.getSummaryHash()).isEqualTo(dayResult.summaryHash());
}
```

- [ ] **Step 7: Implement deterministic hashes**

Use `Locale.ROOT` and include all relevant values:

```java
private String computeEntriesHashForDay(NutritionDay day) {
    String payload = day.entries().stream()
            .sorted(Comparator.comparing(fe -> Optional.ofNullable(fe.externalEntryId())
                    .map(String::valueOf).orElse(fe.name())))
            .map(e -> String.format(Locale.ROOT, "%s|%s|%s|%s|%d|%.4f|%.4f|%.4f",
                    Optional.ofNullable(e.externalEntryId()).map(String::valueOf).orElse("null"),
                    Optional.ofNullable(e.externalFoodId()).map(String::valueOf).orElse("null"),
                    e.name(),
                    Optional.ofNullable(e.mealType()).orElse("null"),
                    e.calories(),
                    e.protein(),
                    e.fat(),
                    e.carbohydrate()))
            .collect(Collectors.joining(";"));
    return DigestUtils.sha256Hex(payload);
}
```

Use one helper for summary hash:

```java
private String computeSummaryHash(Long userId, LocalDate date, int calories, double protein, double fat, double carbs) {
    return DigestUtils.sha256Hex(String.format(Locale.ROOT, "%d|%d|%d|%.4f|%.4f|%.4f",
            userId,
            date.toEpochDay(),
            calories,
            protein,
            fat,
            carbs));
}
```

- [ ] **Step 8: Update save methods to return changed status**

For unchanged day, return `changed=false` and skip entry delete/save. For changed day, update aggregates, `summaryHash`, `entriesHash`, and legacy `externalHash` for backward compatibility.

For month save, compare only `summaryHash`. Return only dates whose summary changed or whose row did not exist.

- [ ] **Step 9: Run narrow nutrition persistence tests**

Run:

```bash
mvn "-Dtest=NutritionPersistenceAdapterIdempotencyTest" test
```

Expected: all pass.

- [ ] **Step 10: Commit iteration 2**

```bash
git add src/main/resources/db/migration/V11__split_nutrition_sync_hashes.sql \
        src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/entity/FatsecretJpaDay.java \
        src/main/java/com/fit/fitnessapp/nutrition/domain/NutritionDaySaveResult.java \
        src/main/java/com/fit/fitnessapp/nutrition/domain/NutritionMonthSaveResult.java \
        src/main/java/com/fit/fitnessapp/nutrition/application/port/out/NutritionCommandPort.java \
        src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/NutritionPersistenceAdapter.java \
        src/test/java/com/fit/fitnessapp/nutrition/NutritionPersistenceAdapterIdempotencyTest.java
git commit -m "fix: track nutrition sync freshness hashes"
```

---

## Iteration 3: Optimize Nutrition Sync Calls And Events

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionService.java`
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionSyncScheduler.java`
- Modify: `src/main/java/com/fit/fitnessapp/api/NutritionSyncedEvent.java`
- Test: `src/test/java/com/fit/fitnessapp/nutrition/NutritionServiceSyncWorkflowTest.java`
- Test: `src/test/java/com/fit/fitnessapp/nutrition/application/service/NutritionSyncSchedulerTest.java`

- [ ] **Step 1: Add service workflow tests**

Create `NutritionServiceSyncWorkflowTest`:

```java
@ExtendWith(MockitoExtension.class)
class NutritionServiceSyncWorkflowTest {

    @Mock FatSecretApiPort apiPort;
    @Mock NutritionCommandPort commandPort;
    @Mock ApplicationEventPublisher eventPublisher;

    @Test
    void syncDayPublishesEventOnlyWhenDayChanged() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        FatSecretToken token = new FatSecretToken("access", "secret");
        NutritionDay day = new NutritionDay(42L, date, List.of(entry(1L, 10L)));

        when(commandPort.getToken(42L)).thenReturn(Optional.of(token));
        when(apiPort.fetchAndParseFoodEntries(token, 42L, date.toEpochDay())).thenReturn(day);
        when(commandPort.saveNutritionDay(day)).thenReturn(new NutritionDaySaveResult(
                42L, date, true, "summary", "entries", 500, 30.0, 10.0, 50.0));

        new NutritionService(apiPort, commandPort, eventPublisher).syncDay(42L, date);

        verify(eventPublisher).publishEvent(new NutritionSyncedEvent(
                42L, date, 500, 30.0, 10.0, 50.0, true, "summary", "entries"));
    }
}
```

Add a sibling test with `changed=false` and verify no event is published.

- [ ] **Step 2: Extend `NutritionSyncedEvent`**

Change event to:

```java
public record NutritionSyncedEvent(
        Long userId,
        LocalDate date,
        int totalCalories,
        double totalProtein,
        double totalFat,
        double totalCarbohydrate,
        boolean changed,
        String summaryHash,
        String entriesHash
) {}
```

Update `FitnessAiServiceDelegationTest` and any event construction.

- [ ] **Step 3: Implement changed-only event publishing**

In `syncDay`, use the `NutritionDaySaveResult`. Publish only when changed:

```java
NutritionDaySaveResult result = nutritionCommandPort.saveNutritionDay(nutritionDay);
if (result.changed()) {
    eventPublisher.publishEvent(new NutritionSyncedEvent(
            result.userId(),
            result.date(),
            result.totalCalories(),
            result.protein(),
            result.fat(),
            result.carbohydrate(),
            true,
            result.summaryHash(),
            result.entriesHash()));
}
```

- [ ] **Step 4: Optimize `syncMonth` detail fetching**

In `syncMonth`, call `saveNutritionMonth` first. Fetch full entries only for:

- dates in `monthResult.changedDates()`
- dates in the configurable recent detail window

Use a property with default:

```java
@Value("${nutrition.sync.detail-window-days:3}")
private int detailWindowDays;
```

Compute:

```java
Set<LocalDate> detailDates = new LinkedHashSet<>(monthResult.changedDates());
LocalDate today = LocalDate.now();
for (int i = 0; i < detailWindowDays; i++) {
    detailDates.add(today.minusDays(i));
}
```

Only fetch dates that are inside the month returned by FatSecret.

- [ ] **Step 5: Add scheduler sliding-window tests**

Change scheduler from `syncAllUsersToday` to a configured window method or add a second scheduled method. Test:

```java
@Test
void syncsRecentWindowForEveryConnectedUser() {
    Clock clock = Clock.fixed(Instant.parse("2026-07-06T20:30:00Z"), ZoneOffset.UTC);
    when(nutritionCommandPort.getAllConnectedUserIds()).thenReturn(List.of(42L));

    new NutritionSyncScheduler(syncUseCase, nutritionCommandPort, clock, 2).syncAllUsersRecentWindow();

    verify(syncUseCase).syncDay(42L, LocalDate.of(2026, 7, 6));
    verify(syncUseCase).syncDay(42L, LocalDate.of(2026, 7, 5));
}
```

- [ ] **Step 6: Run narrow service tests**

Run:

```bash
mvn "-Dtest=NutritionServiceSyncWorkflowTest,NutritionSyncSchedulerTest,FitnessAiServiceDelegationTest" test
```

Expected: all pass.

- [ ] **Step 7: Commit iteration 3**

```bash
git add src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionService.java \
        src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionSyncScheduler.java \
        src/main/java/com/fit/fitnessapp/api/NutritionSyncedEvent.java \
        src/test/java/com/fit/fitnessapp/nutrition/NutritionServiceSyncWorkflowTest.java \
        src/test/java/com/fit/fitnessapp/nutrition/application/service/NutritionSyncSchedulerTest.java \
        src/test/java/com/fit/fitnessapp/ai/FitnessAiServiceDelegationTest.java
git commit -m "fix: reduce nutrition sync churn"
```

---

## Iteration 4: Add Workout Import Event And AI Readiness Signal

**Files:**
- Create: `src/main/java/com/fit/fitnessapp/api/WorkoutImportedEvent.java`
- Modify: `src/main/java/com/fit/fitnessapp/workout/application/service/WorkoutImportService.java`
- Modify: `src/main/java/com/fit/fitnessapp/workout/application/port/out/WorkoutPersistencePort.java`
- Optional create: `src/main/java/com/fit/fitnessapp/workout/domain/WorkoutPersistenceResult.java`
- Test: `src/test/java/com/fit/fitnessapp/workout/WorkoutImportServiceTest.java`

- [ ] **Step 1: Add event contract**

Create:

```java
package com.fit.fitnessapp.api;

import java.time.LocalDate;

public record WorkoutImportedEvent(
        Long userId,
        LocalDate fromDate,
        LocalDate toDate,
        int importedSessions,
        int warningCount
) {}
```

- [ ] **Step 2: Add event publishing test**

Extend `WorkoutImportServiceTest`:

```java
@Test
void importWorkoutsPublishesAffectedDateRangeEvent() {
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    WorkoutImportService service = new WorkoutImportService(List.of(parser), persistencePort, eventPublisher);
    WorkoutSession first = session(1L, LocalDateTime.of(2026, 7, 1, 18, 0));
    WorkoutSession second = session(2L, LocalDateTime.of(2026, 7, 5, 18, 0));
    WorkoutImportResult parseResult = WorkoutImportResult.from(List.of(first, second), List.of());

    when(parser.supports("jefit-csv")).thenReturn(true);
    when(parser.parse(stream)).thenReturn(parseResult);

    service.importWorkouts(stream, "jefit-csv", 42L);

    verify(eventPublisher).publishEvent(new WorkoutImportedEvent(
            42L,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 5),
            2,
            0));
}
```

- [ ] **Step 3: Implement event publishing**

Add `ApplicationEventPublisher` to the service constructor and publish after persistence succeeds.

- [ ] **Step 4: Run workout service tests**

Run:

```bash
mvn "-Dtest=WorkoutImportServiceTest" test
```

Expected: all pass.

- [ ] **Step 5: Commit iteration 4**

```bash
git add src/main/java/com/fit/fitnessapp/api/WorkoutImportedEvent.java \
        src/main/java/com/fit/fitnessapp/workout/application/service/WorkoutImportService.java \
        src/test/java/com/fit/fitnessapp/workout/WorkoutImportServiceTest.java
git commit -m "feat: publish workout import events"
```

---

## Iteration 5: Introduce Daily Insight Snapshot With Workout Context

**Files:**
- Create: `src/main/java/com/fit/fitnessapp/api/DailyInsightRequestedEvent.java`
- Create: `src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightSnapshot.java`
- Create: `src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightSnapshotService.java`
- Modify: `src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightService.java`
- Modify: `src/main/resources/ai/prompts/daily-insight-v1.md`
- Test: `src/test/java/com/fit/fitnessapp/ai/application/service/DailyInsightServiceTest.java`
- Test: `src/test/java/com/fit/fitnessapp/ai/AiPromptRendererTest.java`

- [ ] **Step 1: Add snapshot record**

Create:

```java
package com.fit.fitnessapp.ai.application.service;

import java.time.LocalDate;
import java.util.Map;

public record DailyInsightSnapshot(
        Long userId,
        LocalDate date,
        int totalCalories,
        double protein,
        double fat,
        double carbohydrate,
        int workoutSessions,
        double workoutVolumeKg,
        Map<String, Object> sourceMetadata
) {}
```

- [ ] **Step 2: Add daily workout query seam**

Prefer adding a public API in the workout module instead of making AI depend on workout internals:

```java
public interface WorkoutDailyApi {
    WorkoutDailyStatsDto getDailyStats(Long userId, LocalDate date);
}
```

Implement it in `WorkoutJdbcQueryAdapter`.

- [ ] **Step 3: Add stale insight test**

Extend `DailyInsightServiceTest`:

```java
@Test
void regeneratesDailyInsightWhenSnapshotHashChanged() {
    AiInsightEntity existing = AiInsightEntity.builder()
            .userId(42L)
            .date(LocalDate.of(2026, 7, 6))
            .insightType(InsightType.DAILY)
            .metadata(Map.of("snapshot_hash", "old"))
            .build();

    when(insightRepository.findByUserIdAndDateAndInsightType(42L, date, InsightType.DAILY))
            .thenReturn(Optional.of(existing));
    when(snapshotService.build(42L, date)).thenReturn(snapshotWithHash("new"));

    service.generate(42L, date);

    verify(moeOrchestrator).route(anyString(), eq(MoeOrchestrator.AiTaskType.DAILY_INSIGHT));
}
```

- [ ] **Step 4: Update prompt fixture**

Add workout variables to `daily-insight-v1.md`:

```md
Daily workouts:
Sessions: {{workoutSessions}}, Volume: {{workoutVolumeKg}} kg.
```

- [ ] **Step 5: Run AI prompt and daily service tests**

Run:

```bash
mvn "-Dtest=DailyInsightServiceTest,AiPromptRendererTest" test
```

Expected: all pass.

- [ ] **Step 6: Commit iteration 5**

```bash
git add src/main/java/com/fit/fitnessapp/api/DailyInsightRequestedEvent.java \
        src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightSnapshot.java \
        src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightSnapshotService.java \
        src/main/java/com/fit/fitnessapp/ai/application/service/DailyInsightService.java \
        src/main/resources/ai/prompts/daily-insight-v1.md \
        src/test/java/com/fit/fitnessapp/ai/application/service/DailyInsightServiceTest.java \
        src/test/java/com/fit/fitnessapp/ai/AiPromptRendererTest.java
git commit -m "feat: include workout context in daily insights"
```

---

## Iteration 6: PostgreSQL Integration Gate When Docker Is Available

**Files:**
- Modify or extend: `src/test/java/com/fit/fitnessapp/workout/adapter/out/WorkoutJdbcQueryAdapterIntegrationTest.java`
- Create: `src/test/java/com/fit/fitnessapp/workout/adapter/out/persistence/WorkoutPersistenceAdapterIntegrationTest.java`
- Create: `src/test/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/NutritionPersistenceAdapterIntegrationTest.java`

- [ ] **Step 1: Add workout reimport integration test**

Use Testcontainers PostgreSQL. Insert or import the same workout twice and assert:

```java
assertThat(countRows("workout")).isEqualTo(1);
assertThat(countRows("workout_exercises")).isEqualTo(2);
assertThat(countRows("workout_sets")).isEqualTo(4);
```

- [ ] **Step 2: Add nutrition month no-churn integration test**

Run `saveNutritionDay`, then `saveNutritionMonth` with same totals, then assert `changedDates` is empty and row count did not grow.

- [ ] **Step 3: Run integration gate**

Only when Docker Desktop is running:

```bash
mvn verify -Pintegration
```

Expected: PostgreSQL integration tests pass. If Docker is off, record this gate as skipped with the reason.

- [ ] **Step 4: Commit iteration 6**

```bash
git add src/test/java/com/fit/fitnessapp/workout/adapter/out/persistence/WorkoutPersistenceAdapterIntegrationTest.java \
        src/test/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/NutritionPersistenceAdapterIntegrationTest.java
git commit -m "test: cover ingestion idempotency with postgres"
```

---

## Iteration 7: Full Verification And Architecture Gate

- [ ] **Step 1: Run fast default gate**

```bash
mvn test
```

Expected: all default tests pass.

- [ ] **Step 2: Run Spring Modulith gate**

```bash
mvn test -Parchitecture
```

Expected: architecture tests pass, no new module boundary violations.

- [ ] **Step 3: Run integration gate if Docker is available**

```bash
mvn verify -Pintegration
```

Expected: PostgreSQL integration tests pass. If Docker is unavailable, do not claim this gate passed.

- [ ] **Step 4: Rebuild Graphify after code changes**

```bash
npx graphify hook-rebuild
```

Expected: graph rebuild completes. If `tree-sitter-powershell` is unavailable for the privacy hook script, record it as a known non-code extraction warning.

- [ ] **Step 5: Run privacy hook**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .codex/hooks/privacy-scan.ps1
```

Expected: no secret or private-data findings.

---

## Recommended Execution Order

1. Iteration 1 first. It fixes the highest-confidence workout idempotency bug.
2. Iteration 2 second. It fixes the nutrition hash model and enables meaningful change detection.
3. Iteration 3 third. It reduces FatSecret API calls and event noise after save results exist.
4. Iteration 4 fourth. It gives workout import a public downstream signal.
5. Iteration 5 fifth. It connects nutrition and workout freshness to Daily Insight.
6. Iteration 6 waits for Docker Desktop.
7. Iteration 7 is the final gate before PR or merge.

## External API Notes

- FatSecret `food_entries.get.v2` accepts `date` as days since January 1, 1970 and returns food diary entries for that date.
- FatSecret `food_entries.get_month.v2` returns daily nutrition summaries for a month, and days without entries are not included.
- FatSecret docs say method-based `server.api` calls may continue, but URL-based path integrations are recommended for new integrations.
- FatSecret `exercise_entries.get.v2` returns daily exercise/activity entries and calories; it is not a replacement for Jefit strength-set data.

## Acceptance Criteria

- Importing the same Jefit file twice does not increase `workout`, `workout_exercises`, or `workout_sets` counts.
- Reimporting an edited Jefit session replaces sets and removes stale exercises for that session.
- `saveNutritionDay` detects changes when any macro value changes.
- `saveNutritionMonth` does not churn rows after an equivalent full-day save.
- `syncMonth` fetches full day details only for changed dates plus a small recent safety window.
- `NutritionSyncedEvent` carries enough freshness metadata for AI staleness decisions.
- Daily Insight receives both nutrition and workout context.
- `mvn test` and `mvn test -Parchitecture` pass.
- `mvn verify -Pintegration` passes when Docker Desktop is running.
