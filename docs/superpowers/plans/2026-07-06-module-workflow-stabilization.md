# Module Workflow Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize the nutrition-to-AI-to-Telegram workflows and reduce cross-module coupling without requiring Docker/Testcontainers.

**Architecture:** Keep the existing modular monolith and hexagonal package shape. Make the daily nutrition workflow explicit first, then split AI responsibilities only where the current workflow code is already too broad. Prefer public events and narrow ports over direct module implementation imports.

**Tech Stack:** Java 21, Spring Boot, Spring Modulith, JUnit 5, Mockito, AssertJ, Maven.

---

### Task 1: Daily Nutrition Workflow

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionSyncScheduler.java`
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/application/port/out/NutritionCommandPort.java`
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/NutritionPersistenceAdapter.java`
- Test: `src/test/java/com/fit/fitnessapp/nutrition/NutritionSyncSchedulerTest.java`
- Test: `src/test/java/com/fit/fitnessapp/nutrition/NutritionServiceTest.java`

- [ ] Write failing scheduler tests for connected users, date selection, and failure isolation.
- [ ] Write failing service test for daily sync event publication.
- [ ] Implement the smallest scheduler and port changes.
- [ ] Run `mvn "-Dtest=NutritionSyncSchedulerTest,NutritionServiceTest,NutritionServicePrivacyLoggingTest" test`.
- [ ] Commit the daily workflow fix.

### Task 2: AI Workflow Decomposition

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java`
- Create: focused AI workflow services under `src/main/java/com/fit/fitnessapp/ai/application/service/`
- Test: focused unit tests under `src/test/java/com/fit/fitnessapp/ai/`

- [ ] Add tests around daily insight and Telegram ask behavior before moving code.
- [ ] Extract daily insight generation into a focused service.
- [ ] Extract Telegram ask response handling into a focused service.
- [ ] Keep `FitnessAiService` as the event listener facade.
- [ ] Run the focused AI tests and commit.

### Task 3: Event Seam Consistency

**Files:**
- Modify event packages/listeners only where the behavior is cross-module.
- Test: `src/test/java/com/fit/fitnessapp/module/ModuleArchitectureTest.java` through the architecture profile.

- [ ] Decide which Telegram AI response event is intentionally synchronous.
- [ ] Move or annotate public events consistently when needed.
- [ ] Run `mvn test -Parchitecture`.
- [ ] Commit event seam cleanup.

### Task 4: Telegram Rate Limit Port

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandler.java`
- Create: narrow Telegram-facing rate limit port/service if needed.
- Test: `src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandlerTest.java`

- [ ] Write/adjust failing handler test so Telegram depends on a Telegram-facing limiter abstraction.
- [ ] Implement adapter around the existing AI rate limiter.
- [ ] Run the handler tests and architecture profile.
- [ ] Commit rate limit seam cleanup.

### Task 5: Encoding And Workflow Readability

**Files:**
- Modify corrupted comments/display names only in touched files.
- Test: `src/test/java/com/fit/fitnessapp/CodeHygieneTest.java`

- [ ] Clean mojibake comments in workflow-adjacent files.
- [ ] Run `mvn "-Dtest=CodeHygieneTest" test`.
- [ ] Run `mvn test`.
- [ ] Commit readability cleanup.
