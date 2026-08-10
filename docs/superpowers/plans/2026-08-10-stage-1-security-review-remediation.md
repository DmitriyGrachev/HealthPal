# Stage 1 Security Review Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the three validated Stage 1 findings by making durable event and Telegram work explicitly owner-bound and revocable.

**Architecture:** PostgreSQL owns the final concurrency invariant through generated or explicit owner columns and cascading foreign keys. Telegram application services use short active-link locks for owned writes and revalidate authorization immediately before external delivery; stale delayed work becomes a no-op.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA/JDBC, Spring Modulith, PostgreSQL 16, Flyway, Testcontainers, JUnit 5, AssertJ, Mockito.

## Global Constraints

- Preserve anonymous Telegram replies required before account linking.
- Never hold a database transaction or lock across Telegram network I/O.
- Never log Telegram message, AI response, note, token, or prompt content.
- Do not call real Telegram, OpenRouter, Gemini, FatSecret, or embedding providers in tests.
- Use PostgreSQL/Testcontainers for migration, FK, cascade, and race behavior; do not use H2.
- Existing Flyway migrations are immutable; add only `V27__own_durable_user_work.sql`.
- Preserve unrelated dirty worktree changes.
- Do not stage, commit, push, create a branch, or open a pull request.

---

### Task 1: Durable ownership migration

**Files:**
- Create: `src/main/resources/db/migration/V27__own_durable_user_work.sql`
- Create: `src/test/java/com/fit/fitnessapp/infrastructure/persistence/DurableOwnershipMigrationIntegrationTest.java`

**Interfaces:**
- Produces: `event_publication.user_id`, `telegram_delivery_outbox.user_id`, `conversation_state.user_id`, and `conversation_history.user_id`.
- Produces: composite FK ownership through `telegram_users(user_id, chat_id)` and event ownership through `users(id)`.

- [ ] **Step 1: Write the failing migration integration tests**

Create a unique PostgreSQL schema, migrate it to V26, insert literal legacy fixtures, then migrate to V27. Assert:

```java
assertThat(ownerOfEvent(validEventId)).isEqualTo(userId);
assertThat(ownerOfEvent(malformedEventId)).isNull();
assertThat(rowExists(orphanUserEventId)).isFalse();
assertThat(ownerOfOutbox(linkedOutboxId)).isEqualTo(userId);
assertThat(rowExists(unownedPendingOutboxId)).isFalse();
assertThat(rowExists(unownedSentOutboxId)).isTrue();
assertThat(ownerOfState(chatId)).isEqualTo(userId);
assertThat(ownerOfHistory(historyId)).isEqualTo(userId);
```

Then delete the link and user and assert composite/event cascades remove owned rows without touching another user's rows.

- [ ] **Step 2: Run the migration test and verify RED**

Run:

```text
mvn "-Dit.test=DurableOwnershipMigrationIntegrationTest" verify -Pintegration
```

Expected: FAIL because V27 and its owner columns do not exist.

- [ ] **Step 3: Implement V27**

The migration must:

```sql
CREATE FUNCTION fitnessapp_event_user_id(payload TEXT)
RETURNS BIGINT
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $$
DECLARE raw_user_id TEXT;
BEGIN
    IF NOT (payload IS JSON) THEN RETURN NULL; END IF;
    raw_user_id := payload::jsonb ->> 'userId';
    IF raw_user_id !~ '^[1-9][0-9]*$' THEN RETURN NULL; END IF;
    RETURN raw_user_id::BIGINT;
EXCEPTION WHEN numeric_value_out_of_range OR invalid_text_representation THEN
    RETURN NULL;
END;
$$;
```

Use it for a stored generated event owner, delete event rows with missing referenced users, and add the FK/index. Add/backfill Telegram owner columns, delete ambiguous/orphan active work, deterministically retain one legacy link per private chat, enforce `UNIQUE(chat_id)`, retain only terminal anonymous outbox audit rows, then add NOT NULL and composite cascade constraints where required.

- [ ] **Step 4: Run the migration test and verify GREEN**

Run the same integration command. Expected: all migration tests pass.

### Task 2: Owned Telegram outbox and delivery authorization

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/telegram/application/service/TelegramBotService.java`
- Modify: `src/main/java/com/fit/fitnessapp/telegram/adapter/in/TelegramNotificationListener.java`
- Modify: `src/test/java/com/fit/fitnessapp/telegram/application/service/TelegramBotServiceTest.java`
- Modify: `src/test/java/com/fit/fitnessapp/telegram/adapter/in/TelegramNotificationListenerTest.java`

**Interfaces:**
- Produces: `boolean TelegramBotService.enqueueOwnedMessage(Long userId, Long chatId, String text)`.
- Produces: `OutboxItem(Long id, Long userId, Long chatId, String text, int attempts, int maxAttempts)`.

- [ ] **Step 1: Write failing owned-delivery unit tests**

Add tests proving:

```java
assertThat(service.enqueueOwnedMessage(7L, 100L, "private")).isTrue();
assertThat(service.enqueueOwnedMessage(7L, 999L, "private")).isFalse();
```

The SQL must insert through an active `(user_id, chat_id)` link and lock it for the short transaction. Extend the retry test so an owned item whose link no longer exists causes zero `AbsSender.execute` calls, while anonymous items remain deliverable.

Update the notification listener test to require `(userId, chatId)` owned enqueue.

- [ ] **Step 2: Run focused tests and verify RED**

```text
mvn "-Dtest=TelegramBotServiceTest,TelegramNotificationListenerTest" test
```

Expected: compilation/assertion failure because the owned API and owner-aware worker do not exist.

- [ ] **Step 3: Implement owned enqueue and worker checks**

Use `INSERT ... SELECT` from `telegram_users WHERE user_id = ? AND chat_id = ? FOR KEY SHARE`. Claim owned rows only when the same link exists. Immediately before `executeTelegramSend`, query the active link again; if absent, remove/mark the row with stable code `TELEGRAM_LINK_REVOKED` and do not call Telegram.

Change `TelegramNotificationListener` to call `enqueueOwnedMessage(event.userId(), chatId, message)`.
Route Note and Weight confirmations containing user content through the same owned queue; keep anonymous sends only for genuine pre-link or non-personal responses.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the focused command and expect all tests green.

### Task 3: Atomic unlink and delayed AI response revocation

**Files:**
- Create: `src/main/java/com/fit/fitnessapp/telegram/application/service/TelegramLinkRevocationService.java`
- Modify: `src/main/java/com/fit/fitnessapp/telegram/adapter/in/web/TelegramLinkController.java`
- Modify: `src/main/java/com/fit/fitnessapp/telegram/adapter/in/TelegramAiResponseListener.java`
- Modify: `src/test/java/com/fit/fitnessapp/telegram/adapter/in/web/TelegramLinkControllerTest.java`
- Create: `src/test/java/com/fit/fitnessapp/telegram/adapter/in/TelegramAiResponseListenerTest.java`
- Modify: `src/test/java/com/fit/fitnessapp/auth/UserDataLifecycleServiceIntegrationTest.java`

**Interfaces:**
- Produces: `void TelegramLinkRevocationService.unlink(Long userId)`.
- Consumes: `TelegramBotService.enqueueOwnedMessage(Long, Long, String)`.

- [ ] **Step 1: Write failing unit and PostgreSQL regression tests**

Add tests for:

```java
listener.onAiResponse(new TelegramAiResponseEvent(userId, chatId, "private"));
verify(botService).enqueueOwnedMessage(userId, chatId, "private");
verify(botService, never()).sendMessage(anyLong(), anyString());
```

Controller unlink must delegate to the revocation service. Integration tests must queue an owned row, unlink, run the worker, and assert zero external sends/no row. A latch race between owned enqueue and unlink must end with no link and no owned row.

- [ ] **Step 2: Run focused tests and verify RED**

```text
mvn "-Dtest=TelegramLinkControllerTest,TelegramAiResponseListenerTest" test
mvn "-Dit.test=UserDataLifecycleServiceIntegrationTest" verify -Pintegration
```

Expected: missing service/API and surviving queued delivery.

- [ ] **Step 3: Implement revocation flow**

`TelegramLinkRevocationService.unlink` runs transactionally, locks `findByUserIdForUpdate(userId)`, and deletes that entity. Composite FK cascades revoke owned state/history/outbox. The controller delegates to it. The AI listener queues an owned response instead of sending immediately.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run both focused commands and expect all tests green.

### Task 4: Owner-bound conversation state

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/telegram/application/port/in/ConversationStateUseCase.java`
- Modify: `src/main/java/com/fit/fitnessapp/telegram/application/service/ConversationStateService.java`
- Modify: `src/main/java/com/fit/fitnessapp/telegram/infrastructure/persistence/entity/ConversationStateEntity.java`
- Modify: `src/main/java/com/fit/fitnessapp/telegram/infrastructure/persistence/repository/TelegramUserRepository.java`
- Modify: `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/WeightCommandHandler.java`
- Modify: `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/NoteCommandHandler.java`
- Create: `src/test/java/com/fit/fitnessapp/telegram/application/service/ConversationStateServiceTest.java`
- Modify: `src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/WeightCommandHandlerTest.java`
- Modify: `src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/NoteCommandHandlerTest.java`
- Modify: `src/test/java/com/fit/fitnessapp/auth/UserDataLifecycleServiceIntegrationTest.java`

**Interfaces:**
- Produces: `boolean updateState(Long userId, Long chatId, ConversationState state)`.
- Produces: `boolean updateState(Long userId, Long chatId, ConversationState state, Map<String,Object> data)`.
- Produces: `Optional<TelegramUserEntity> findByUserIdAndChatIdForUpdate(Long userId, Long chatId)`.

- [ ] **Step 1: Write failing state tests**

The service test must show an active link saves a state carrying `userId`; a missing link returns `false` and never saves. Handler tests must verify the already resolved user ID is passed. Integration tests must delete/unlink first, invoke the stale state update, and assert no state row exists.

- [ ] **Step 2: Run focused tests and verify RED**

```text
mvn "-Dtest=ConversationStateServiceTest,WeightCommandHandlerTest,NoteCommandHandlerTest" test
```

Expected: missing owner-aware signatures and entity field.

- [ ] **Step 3: Implement owner-aware state writes**

Add `userId` to `ConversationStateEntity`. Lock the exact link before save; return `false` if absent. Update Weight and Note handlers to pass `userOpt.get().getUserId()` for every state creation/update. Reads and clear remain chat-keyed.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run focused unit tests, then the lifecycle integration test; expect all green.

### Task 5: Publication race, review, and final gates

**Files:**
- Modify: `src/test/java/com/fit/fitnessapp/auth/UserDataLifecycleServiceIntegrationTest.java`
- Modify only if required by a demonstrated failure: `src/main/java/com/fit/fitnessapp/infrastructure/events/EventPublicationUserDataLifecycleParticipant.java`
- Modify: `.graphify/graph.json` and generated Graphify reports through `npx graphify hook-rebuild` only.

**Interfaces:**
- Consumes: generated `event_publication.user_id` and FK cascade from Task 1.

- [ ] **Step 1: Add the concurrent publication/deletion test**

Use two transactions and latches. Hold a user-owned `event_publication` insert until deletion overlaps, then release it. The observable postcondition is:

```java
assertThat(count("users", "id", userId)).isZero();
assertThat(serializedPublicationCount(userId)).isZero();
```

The V26→V27 migration test from Task 1 supplies the observed RED for this production change. This full-Spring test verifies the same invariant through the real lifecycle coordinator and must not merely assert implementation SQL text.

- [ ] **Step 2: Run the lifecycle integration test**

```text
mvn "-Dit.test=UserDataLifecycleServiceIntegrationTest" verify -Pintegration
```

Expected after Task 1: all scenarios green. If the test exposes a real gap, change only the smallest ownership/cleanup boundary and rerun RED/GREEN.

- [ ] **Step 3: Run independent permitted review**

Dispatch only the user-authorized reviewer configuration `gpt-5.6-sol medium`. Review the complete security remediation diff against the three supplied findings. Fix only validated findings using a new failing test first.

- [ ] **Step 4: Rebuild Graphify and run final gates**

```text
npx graphify hook-rebuild
$changedJava = (git status --short | ForEach-Object { $_.Substring(3) } | Where-Object { $_ -like '*.java' }) -join ','
graphify review-analysis --graph .graphify/graph.json --files $changedJava
mvn test
mvn test -Parchitecture
mvn verify -Pintegration
git diff --check
powershell -File .codex/hooks/privacy-scan.ps1
```

Report exact totals, Graphify warnings, remaining risks, and the preserved dirty-worktree state. Do not mutate Git index or history.
