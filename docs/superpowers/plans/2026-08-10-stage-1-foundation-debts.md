# Stage 1 Foundation Debts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move User data lifecycle work to module owners, make deletion safe against event replay, and require explicit classification before personal data can leave through an AI provider.

**Architecture:** `auth` remains the synchronous transaction coordinator and discovers small lifecycle participants through the existing shared API named interface. Each module owns its SQL, while database constraints and replay guards prevent stale events from recreating derived data. `MoeOrchestrator` becomes the single classified AI egress seam and validates policy before execution guard, budget, fallback, or provider work.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Modulith, Spring Data/JdbcTemplate, PostgreSQL 16, Flyway, pgvector, JUnit 5, AssertJ, Mockito, Testcontainers.

## Global Constraints

- Do not commit, stage, push, or modify unrelated dirty-worktree files.
- Do not modify existing Flyway migrations; add `V26__add_user_memory_owner.sql`.
- Do not add frameworks, dependencies, providers, UI, Telegram features, or a new lifecycle module.
- Do not call real OpenRouter, Gemini, Telegram, or FatSecret APIs.
- Keep the export JSON compatible; add only `aiUsageBudget`.
- Keep account deletion synchronous and atomic; delete auth identity last.
- Preserve SmartAiRouter fallback, timeouts, budgets, cooldowns, prompt templates, and response contracts.
- Treat `mvn verify -Pintegration` baseline as environmentally blocked until Docker/Testcontainers is available; rerun it at final verification.
- Plan commit steps are intentionally omitted because the task explicitly prohibits commits.

---

## File Structure

### Shared lifecycle and replay contracts

- Create `src/main/java/com/fit/fitnessapp/api/UserDataLifecycleParticipant.java`: stable lifecycle SPI.
- Create `src/main/java/com/fit/fitnessapp/api/UserDataExportFragment.java`: immutable keyed fragment.
- Create `src/main/java/com/fit/fitnessapp/api/InsightSourceApi.java`: replay source-existence query.
- Create `src/main/java/com/fit/fitnessapp/auth/api/UserDataPresenceApi.java`: User and User Note existence query.

### Module-owned lifecycle adapters

- Create `src/main/java/com/fit/fitnessapp/auth/adapter/out/persistence/AuthUserDataLifecycleParticipant.java`.
- Create `src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/NutritionUserDataLifecycleParticipant.java`.
- Create `src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/WorkoutUserDataLifecycleParticipant.java`.
- Create `src/main/java/com/fit/fitnessapp/ai/adapter/out/persistence/AiUserDataLifecycleParticipant.java`.
- Create `src/main/java/com/fit/fitnessapp/memory/adapter/out/persistence/MemoryUserDataLifecycleParticipant.java`.
- Create `src/main/java/com/fit/fitnessapp/telegram/infrastructure/persistence/TelegramUserDataLifecycleParticipant.java`.
- Create `src/main/java/com/fit/fitnessapp/job/adapter/out/persistence/DurableJobUserDataLifecycleParticipant.java`.
- Create `src/main/java/com/fit/fitnessapp/infrastructure/events/EventPublicationUserDataLifecycleParticipant.java`.

### AI egress and untrusted content

- Create `src/main/java/com/fit/fitnessapp/ai/AiDataClass.java`.
- Create `src/main/java/com/fit/fitnessapp/ai/ClassifiedAiPrompt.java`.
- Create `src/main/java/com/fit/fitnessapp/ai/AiEgressPolicy.java`.
- Create `src/main/java/com/fit/fitnessapp/ai/exception/AiEgressDeniedException.java`.
- Create `src/test/java/com/fit/fitnessapp/ai/AiEgressPolicyTest.java`.

### Existing files to modify

- Lifecycle: `UserDataLifecycleService`, `UserDataExportDto`, `UserRepository`, `UserAdapter`, `UserPort`, `FatSecretProfileSyncService`, repositories needed for owner/source checks.
- Replay: `MemoryEventListener`, `MemoryEventListenerTest`, lifecycle integration test, Telegram replay integration coverage.
- AI: `AiProperties`, `MoeOrchestrator`, four calling services, `AiSafetyService`, `AiContextService`, affected unit tests and `application.properties`/`application-test.properties`/`README.md`.
- Database: create `src/main/resources/db/migration/V26__add_user_memory_owner.sql`; update PostgreSQL integration assertions.

---

### Task 1: Define the lifecycle SPI and coordinator behavior

**Files:**
- Create: `src/main/java/com/fit/fitnessapp/api/UserDataExportFragment.java`
- Create: `src/main/java/com/fit/fitnessapp/api/UserDataLifecycleParticipant.java`
- Modify: `src/main/java/com/fit/fitnessapp/auth/domain/UserDataExportDto.java`
- Modify: `src/main/java/com/fit/fitnessapp/auth/application/service/UserDataLifecycleService.java`
- Rewrite: `src/test/java/com/fit/fitnessapp/auth/application/service/UserDataLifecycleServiceTest.java`

**Interfaces:**
- Produces: `UserDataLifecycleParticipant.key()`, `exportData(Long)`, `deleteData(Long)`, and `disconnectExternalAccount(Long)`.
- Produces: `UserDataExportFragment(String participantKey, Map<String,Object> values)`.
- Consumes: Spring injection of `List<UserDataLifecycleParticipant>` and auth-owned `UserRepository`.

- [ ] **Step 1: Replace SQL-string assertions with a failing coordinator test**

  Construct two fake participants with keys returned out of order. Assert that export invokes them in key order, preserves current fields, and maps `aiUsageBudget`. Assert delete invokes every cleanup before `UserRepository.delete(user)`.

  ```java
  UserDataExportDto export = service.exportUserData(42L);
  assertThat(export.aiUsageBudget()).containsExactly(budgetRow);
  assertThat(invocations).containsExactly("ai:export", "nutrition:export");

  service.deleteAccount(42L);
  inOrder(aiParticipant, nutritionParticipant, userRepository)
          .verify(userRepository).delete(user);
  ```

- [ ] **Step 2: Run the coordinator test and verify RED**

  Run: `mvn "-Dtest=UserDataLifecycleServiceTest" test`

  Expected: compilation/test failure because the SPI and `aiUsageBudget` do not exist.

- [ ] **Step 3: Add the minimal shared SPI**

  ```java
  public record UserDataExportFragment(String participantKey, Map<String, Object> values) {
      public UserDataExportFragment {
          values = Map.copyOf(values);
      }
  }

  public interface UserDataLifecycleParticipant {
      String key();
      UserDataExportFragment exportData(Long userId);
      void deleteData(Long userId);
      default void disconnectExternalAccount(Long userId) { }
  }
  ```

- [ ] **Step 4: Implement the auth coordinator without foreign SQL**

  Inject `UserRepository`, `List<UserDataLifecycleParticipant>`, and `Clock`. Resolve the auth User first, sort participants by `key`, collect fragments, adapt canonical fragment values to the existing DTO, and add `aiUsageBudget`. During delete, run all participant cleanup and call `userRepository.delete(user)` last within the existing transaction.

- [ ] **Step 5: Verify GREEN and the foreign-table invariant**

  Run: `mvn "-Dtest=UserDataLifecycleServiceTest" test`

  Run: `rg -n "profile|fatsecret_|workout|ai_insights|user_memory|telegram_|conversation_|durable_jobs|event_publication" src/main/java/com/fit/fitnessapp/auth/application/service/UserDataLifecycleService.java`

  Expected: test passes; the search returns no foreign table names.

### Task 2: Move FatSecret ownership and implement module participants

**Files:**
- Create all eight participant files listed under “Module-owned lifecycle adapters”.
- Modify: `src/main/java/com/fit/fitnessapp/nutrition/application/service/FatSecretProfileSyncService.java`
- Modify: module repositories only where a derived delete/existence method is simpler than JDBC.
- Delete: `src/main/java/com/fit/fitnessapp/auth/api/UserPort.java`
- Delete: `src/main/java/com/fit/fitnessapp/auth/adapter/out/persistence/UserAdapter.java`
- Modify: `src/main/java/com/fit/fitnessapp/auth/adapter/out/persistence/repository/UserRepository.java`
- Modify: `src/test/java/com/fit/fitnessapp/auth/UserDataLifecycleServiceIntegrationTest.java`

**Interfaces:**
- Consumes: lifecycle SPI from Task 1.
- Produces stable participant keys: `auth`, `nutrition`, `workout`, `ai`, `memory`, `telegram`, `jobs`, `event-publications`.

- [ ] **Step 1: Expand the PostgreSQL lifecycle scenario before adapter implementation**

  Seed two Users. For the first User seed roles/notes, profile/weight/FatSecret connection/day/food, workout/exercise/set/cardio, AI Insight, USER AI budget, User Memory, Telegram Link/state/history/outbox, Durable Job, and completed/incomplete event publications. Seed control data for the second User and a GLOBAL budget.

  Assert export fields, including:

  ```java
  assertThat(export.aiUsageBudget())
          .allSatisfy(row -> {
              assertThat(row).containsEntry("scope_type", "USER");
              assertThat(row).containsEntry("scope_id", userId);
          });
  ```

  After delete, assert every first-User category and exact serialized event content is absent, the second User remains, and the GLOBAL budget remains.

- [ ] **Step 2: Run the focused integration test and verify RED**

  Run: `mvn "-Dit.test=UserDataLifecycleServiceIntegrationTest" verify -Pintegration`

  Expected when Docker is available: failure because participants and AI budget export are missing. If Docker is unavailable, record the same Testcontainers infrastructure error and continue with compilation/unit checks until Docker is started.

- [ ] **Step 3: Implement module-owned export and cleanup SQL**

  Each participant uses its own module’s `JdbcTemplate`/repositories. The event participant uses exact structured matching:

  ```sql
  DELETE FROM event_publication
   WHERE serialized_event IS JSON
     AND serialized_event::jsonb ->> 'userId' = ?
  ```

  The AI participant selects/deletes only `scope_type = 'USER' AND scope_id = ?`. The Telegram participant reads `chat_id` before deleting history/state/outbox/link. The nutrition disconnect method deletes only `fatsecret_connection`.

- [ ] **Step 4: Remove FatSecret knowledge from auth**

  Remove `findUserIdsWithFatSecretTokens()` and its JPQL from `UserRepository`. Inject `NutritionCommandPort` into `FatSecretProfileSyncService` and call `getAllConnectedUserIds()`. Delete `UserPort` and `UserAdapter` after `rg` confirms no remaining callers.

- [ ] **Step 5: Run focused default tests and architecture gate**

  Run: `mvn "-Dtest=UserDataLifecycleServiceTest,NutritionSyncSchedulerTest" test`

  Run: `mvn test -Parchitecture`

  Expected: no module cycle; auth depends only on the shared SPI and its own persistence.

### Task 3: Add constraint-backed User Memory ownership

**Files:**
- Create: `src/main/resources/db/migration/V26__add_user_memory_owner.sql`
- Modify: `src/test/java/com/fit/fitnessapp/memory/MemoryPgVectorIntegrationTest.java`
- Modify: `src/test/java/com/fit/fitnessapp/auth/UserDataLifecycleServiceIntegrationTest.java`
- Modify: memory lifecycle participant from Task 2.

**Interfaces:**
- Produces database column `user_memory.user_id BIGINT GENERATED ALWAYS ... STORED`.

- [ ] **Step 1: Add failing PostgreSQL ownership assertions**

  Assert `user_id` is generated from metadata, indexed, rejects a nonexistent User, and cascades when the User is deleted. Update lifecycle queries to assert against the real owner column.

- [ ] **Step 2: Run the focused PostgreSQL test and verify RED**

  Run: `mvn "-Dit.test=MemoryPgVectorIntegrationTest,UserDataLifecycleServiceIntegrationTest" verify -Pintegration`

  Expected when Docker is available: missing-column/constraint failure.

- [ ] **Step 3: Add the forward migration**

  ```sql
  DELETE FROM user_memory
   WHERE metadata IS NULL
      OR NOT (metadata ? 'user_id')
      OR NOT ((metadata ->> 'user_id') ~ '^[1-9][0-9]*$');

  DELETE FROM user_memory memory
   WHERE NOT EXISTS (
       SELECT 1 FROM users app_user
        WHERE app_user.id = (memory.metadata ->> 'user_id')::bigint
   );

  ALTER TABLE user_memory
      ADD COLUMN user_id BIGINT
      GENERATED ALWAYS AS ((metadata ->> 'user_id')::bigint) STORED;
  CREATE INDEX idx_user_memory_user_id ON user_memory(user_id);
  ALTER TABLE user_memory
      ADD CONSTRAINT fk_user_memory_user
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
  ```

- [ ] **Step 4: Use the owner column in lifecycle persistence**

  Replace module-owned JSON extraction in export/delete with `WHERE user_id = ?`.

- [ ] **Step 5: Re-run focused PostgreSQL tests**

  Run: `mvn "-Dit.test=MemoryPgVectorIntegrationTest,UserDataLifecycleServiceIntegrationTest" verify -Pintegration`

### Task 4: Make event replay a safe no-op for missing owners and sources

**Files:**
- Create: `src/main/java/com/fit/fitnessapp/auth/api/UserDataPresenceApi.java`
- Create: `src/main/java/com/fit/fitnessapp/api/InsightSourceApi.java`
- Modify: `UserRepository`, `UserNoteJpaRepository`, and `AiInsightRepository`.
- Create or modify auth adapter implementing `UserDataPresenceApi`.
- Modify: `src/main/java/com/fit/fitnessapp/memory/application/service/MemoryEventListener.java`
- Modify: `src/test/java/com/fit/fitnessapp/memory/MemoryEventListenerTest.java`
- Modify: `src/test/java/com/fit/fitnessapp/auth/UserDataLifecycleServiceIntegrationTest.java`

**Interfaces:**
- `UserDataPresenceApi.userExists(Long)` and `userNoteExists(Long, Long)`.
- `InsightSourceApi.insightExists(Long, InsightType, LocalDate)`.

- [ ] **Step 1: Add failing listener tests for missing owner/source**

  ```java
  when(userPresence.userExists(42L)).thenReturn(false);
  listener.onInsightGenerated(event);
  verifyNoInteractions(vectorStore);

  when(userPresence.userExists(42L)).thenReturn(true);
  when(userPresence.userNoteExists(42L, 77L)).thenReturn(false);
  listener.onUserNoteCreated(noteEvent);
  verifyNoInteractions(vectorStore);
  ```

- [ ] **Step 2: Verify RED**

  Run: `mvn "-Dtest=MemoryEventListenerTest" test`

- [ ] **Step 3: Implement explicit replay guards**

  Check User existence before all add operations. Check the corresponding note/insight source before adding derived memory. Return normally when absent. Do not catch `DataIntegrityViolationException`.

- [ ] **Step 4: Add one real replay scenario**

  Extend the lifecycle integration test: retain an old `InsightGeneratedEvent`, delete the User, invoke the memory and Telegram listeners, and assert no `user_memory` or `telegram_delivery_outbox` row appears and the publication can complete.

- [ ] **Step 5: Verify unit and integration behavior**

  Run: `mvn "-Dtest=MemoryEventListenerTest,TelegramNotificationListenerTest" test`

  Run: `mvn "-Dit.test=UserDataLifecycleServiceIntegrationTest" verify -Pintegration`

### Task 5: Add classified AI egress policy before budget and providers

**Files:**
- Create: `AiDataClass.java`, `ClassifiedAiPrompt.java`, `AiEgressPolicy.java`, `AiEgressDeniedException.java`.
- Create: `src/test/java/com/fit/fitnessapp/ai/AiEgressPolicyTest.java`
- Modify: `src/main/java/com/fit/fitnessapp/ai/AiProperties.java`
- Modify: `src/main/java/com/fit/fitnessapp/ai/MoeOrchestrator.java`
- Modify: `src/test/java/com/fit/fitnessapp/ai/MoeOrchestratorTest.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application-test.properties`
- Modify: `README.md`

**Interfaces:**
- `ClassifiedAiPrompt(String prompt, AiDataClass classification)`.
- `AiEgressPolicy.validate(ClassifiedAiPrompt)`.
- `MoeOrchestrator.route(Long, ClassifiedAiPrompt, AiTaskType)`.

- [ ] **Step 1: Write failing policy tests**

  Cover PUBLIC allow, SENSITIVE deny by default, SENSITIVE allow with opt-in, SECRET deny, null request deny, and null classification deny. Assert the typed exception and stable code without asserting localized text.

- [ ] **Step 2: Verify policy RED**

  Run: `mvn "-Dtest=AiEgressPolicyTest" test`

- [ ] **Step 3: Implement minimal policy and configuration**

  Add `boolean allowSensitiveExternalEgress` to `AiProperties`. `AiEgressPolicy.validate` uses only that property and the classification enum. Add:

  ```properties
  app.ai.allow-sensitive-external-egress=${AI_ALLOW_SENSITIVE_EXTERNAL_EGRESS:false}
  ```

  Document `AI_ALLOW_SENSITIVE_EXTERNAL_EGRESS` in `README.md`; do not read or modify `.env`.

- [ ] **Step 4: Add the failing orchestrator wiring test**

  Construct a denied SENSITIVE request and verify no interaction with `AiExecutionGuard`, SmartAiRouter, or either provider port. This proves denial precedes budget reservation because budget is reachable only inside the guard.

- [ ] **Step 5: Validate once before the existing guard**

  In `MoeOrchestrator.route`, log classification and prompt length only, call `egressPolicy.validate(request)`, then pass `request.prompt()` to the unchanged `AiExecutionGuard` and provider routing switch.

- [ ] **Step 6: Verify focused policy/orchestrator tests**

  Run: `mvn "-Dtest=AiEgressPolicyTest,MoeOrchestratorTest" test`

### Task 6: Classify all personal prompts and preserve routing behavior

**Files:**
- Modify: `DailyInsightService.java`, `WeeklyReportService.java`, `MonthlyReportService.java`, `TelegramAskAiService.java`.
- Modify affected tests: `DailyInsightServiceTest`, `TelegramAskAiServiceTest`, `FitnessAiServiceReportFreshnessTest`, `AiExternalIoTransactionBoundaryIntegrationTest`, and all `MoeOrchestrator.route` stubs found by `rg`.

**Interfaces:**
- Consumes `ClassifiedAiPrompt` from Task 5.

- [ ] **Step 1: Update tests first to expect SENSITIVE requests**

  Capture `ClassifiedAiPrompt` and assert `classification() == AiDataClass.SENSITIVE` for each logical workflow while retaining the existing prompt string assertions.

- [ ] **Step 2: Run affected unit tests and verify RED**

  Run: `mvn "-Dtest=DailyInsightServiceTest,TelegramAskAiServiceTest,FitnessAiServiceReportFreshnessTest" test`

- [ ] **Step 3: Wrap each personal prompt exactly once**

  ```java
  moeOrchestrator.route(
          userId,
          new ClassifiedAiPrompt(prompt, AiDataClass.SENSITIVE),
          taskType);
  ```

  Do not alter prompt templates, task types, response validation, model selection, or fallback code.

- [ ] **Step 4: Verify all route call sites migrated**

  Run: `rg -n "moeOrchestrator\.route\([^\n]*,\s*prompt\s*," src/main/java src/test/java`

  Expected: no raw prompt call remains.

- [ ] **Step 5: Re-run affected tests**

  Run: `mvn "-Dtest=DailyInsightServiceTest,TelegramAskAiServiceTest,FitnessAiServiceReportFreshnessTest,MoeOrchestratorTest" test`

### Task 7: Apply one untrusted-data boundary to questions, notes, and memories

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/ai/application/service/AiSafetyService.java`
- Modify: `src/main/java/com/fit/fitnessapp/ai/application/service/AiContextService.java`
- Modify: `WeeklyReportService.java`, `MonthlyReportService.java`, `TelegramAskAiService.java`.
- Modify: `src/test/java/com/fit/fitnessapp/ai/application/service/AiSafetyServiceTest.java`
- Modify context/report service tests affected by constructor/signature changes.

**Interfaces:**
- Add `AiSafetyService.UntrustedDataType` with tags `user_question`, `user_note`, and `user_memory`.
- Add `wrapUntrusted(UntrustedDataType, String)` with a fixed maximum content length.

- [ ] **Step 1: Add failing boundary tests**

  Assert mixed-case/whitespace closing tags cannot terminate each allowed boundary, overlong content is bounded, and an unsupported/null type is rejected. Assert ordinary content is retained verbatim apart from trimming, length bounding, and closing-tag escaping.

- [ ] **Step 2: Verify boundary RED**

  Run: `mvn "-Dtest=AiSafetyServiceTest" test`

- [ ] **Step 3: Implement the minimal boundary mechanism**

  Remove semantic-looking code-fence/script rewriting. Bound content length, escape only matching closing tags, and emit an explicit data wrapper such as:

  ```text
  <user_memory data-trust="untrusted">
  ...bounded content...
  </user_memory>
  ```

- [ ] **Step 4: Apply boundaries at content entry points**

  Wrap each Telegram question, each User Note included by weekly/monthly reports, and each memory item formatted by `AiContextService`. Do not wrap generated system instructions or structured nutrition/workout values as user-authored content.

- [ ] **Step 5: Run the requested focused AI gate**

  Run: `mvn "-Dtest=AiEgressPolicyTest,MoeOrchestratorTest,AiSafetyServiceTest" test`

### Task 8: Architecture review, Graphify rebuild, and final verification

**Files:**
- Review all files changed by Tasks 1–7.
- Do not modify unrelated dirty files.

- [ ] **Step 1: Inspect the exact change set**

  Run: `git status --short`

  Run: `git diff -- src/main src/test README.md docs/superpowers`

  Run: `git diff --check`

- [ ] **Step 2: Rebuild Graphify after Java changes when available**

  Run: `npx graphify hook-rebuild`

  Preserve pre-existing Graphify changes and report any overlap instead of reverting them.

- [ ] **Step 3: Review dependency impact**

  Run: `$changedJava = (git diff --name-only -- '*.java') -join ','; graphify review-analysis --graph .graphify/graph.json --files $changedJava`

  Confirm the removed directions are `auth -> foreign persistence` and `nutrition -> auth FatSecret query`.

- [ ] **Step 4: Run the default gate**

  Run: `mvn test`

  Record exact tests/failures/errors/skipped from fresh output.

- [ ] **Step 5: Run the architecture gate**

  Run: `mvn test -Parchitecture`

  Record exact tests/failures/errors/skipped.

- [ ] **Step 6: Run the full PostgreSQL integration gate**

  Run: `mvn verify -Pintegration`

  If Docker remains unavailable, report the exact Testcontainers root cause and counts; do not claim integration success.

- [ ] **Step 7: Run a permitted reviewer pass**

  Use only reviewer model `gpt-5.6-sol` at `medium` effort. Review correctness, transactionality, exact event matching, migration safety, privacy logging, module boundaries, and test gaps. Apply only validated findings through fresh TDD cycles.

- [ ] **Step 8: Produce the final report**

  Include architecture summary, complete changed/created file list, acceptance mapping, exact command results, remaining risks/simplifications, and final `git status --short`. Do not commit, stage, or push.
