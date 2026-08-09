# FND-009 Telegram Private Chat Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure Telegram personal commands and link-status replies can execute only in the linked private chat, preventing group/channel data leaks.

**Architecture:** `TelegramUpdateHandler` owns the universal chat-type boundary and discards every non-private message before handler dispatch. Individual personal handlers retain ownership of sender-to-account lookup and fail closed unless the incoming private `chatId` equals the stored `TelegramUserEntity.chatId`; this avoids coupling the transport dispatcher to repository authorization.

**Tech Stack:** Java 21, Spring Boot, TelegramBots 6.8, JUnit 5, Mockito, AssertJ, Maven.

## Global Constraints

- A chat is private only when `Boolean.TRUE.equals(update.getMessage().getChat().isUserChat())`.
- Non-private updates cause no handler invocation, bot send, event publication, database write, or conversation-state operation.
- `/ask`, `/today`, `/weight`, `/note`, and weight/note continuations require `incomingChatId.equals(linkedUser.getChatId())` before rate-limit consumption or state access.
- Chat-ID mismatch fails silently: no response, event, state mutation, or data write.
- `/start` and `/link` are private-only; valid link persists the incoming private chat ID.
- Never log raw Telegram text, notes, health/nutrition data, tokens, or link codes.
- Run focused Telegram tests and `mvn test`; no Docker/Testcontainers gate is required.

---

### Task 1: Universal private-chat dispatch gate

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/telegram/adapter/in/TelegramUpdateHandler.java`
- Modify: `src/test/java/com/fit/fitnessapp/telegram/adapter/in/TelegramUpdateHandlerPrivacyLoggingTest.java`

**Interfaces:**
- Consumes: `Message.getChat().isUserChat()` from TelegramBots 6.8.
- Produces: only private text updates reach `CommandHandler.canHandle` and `handle`.

- [ ] **Step 1: Add a failing group/channel dispatch test**

Create a helper which mocks `Message`, `Chat`, and `Update`; use `when(chat.isUserChat()).thenReturn(false)`. Test group, supergroup, and channel cases as a parameterized source. The assertion is:

```java
updateHandler.onUpdateReceived(update);

verifyNoInteractions(commandHandler);
assertThat(output).doesNotContain(sensitiveText);
```

Keep `message.hasText()` true and `message.getChatId()` non-null so the failure proves missing chat-type filtering rather than the existing no-text guard.

- [ ] **Step 2: Run RED**

Run: `mvn "-Dtest=TelegramUpdateHandlerPrivacyLoggingTest" test`

Expected: the new test fails because `commandHandler.canHandle(update)` is invoked for a non-private message.

- [ ] **Step 3: Add the early private-chat return**

Immediately after `Message message = update.getMessage();`, before reading text or iterating handlers, add:

```java
if (message.getChat() == null || !Boolean.TRUE.equals(message.getChat().isUserChat())) {
    log.debug("Telegram update ignored status=non_private_chat");
    return;
}
```

The log must not include message text, link code, or personal values. Do not query persistence or invoke a handler in this branch.

- [ ] **Step 4: Run GREEN**

Run: `mvn "-Dtest=TelegramUpdateHandlerPrivacyLoggingTest" test`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
fix: ignore Telegram non-private updates
```

### Task 2: Linked-private-chat authorization for personal handlers

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandler.java`
- Modify: `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/TodayCommandHandler.java`
- Modify: `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/WeightCommandHandler.java`
- Modify: `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/NoteCommandHandler.java`
- Modify: `src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandlerTest.java`
- Modify: `src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/TodayCommandHandlerTest.java` (create if absent)
- Modify: `src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/WeightCommandHandlerTest.java`
- Modify: `src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/NoteCommandHandlerTest.java`

**Interfaces:**
- Consumes: a resolved `TelegramUserEntity` whose `chatId` is the only authorized chat.
- Preserves: command/event record types and successful matching-private-chat behavior.

- [ ] **Step 1: Add one mismatch test per personal handler before production edits**

For each handler, create a linked entity with `chatId = 456L` and invoke an incoming update from `789L`. Assert only the repository lookup may occur. For `/ask`:

```java
handler.handle(update(789L, telegramId, "/ask private question"));

verify(telegramUserRepository).findById(telegramId);
verifyNoInteractions(botService, eventPublisher, aiRateLimitApi);
```

For `/today`, assert no bot/event; for `/weight` and `/note`, assert no bot/event and `verifyNoInteractions(stateUseCase)`.

Update all existing positive fixtures so the linked entity stores the same chat ID as the incoming update; this keeps successful behavior meaningful under the new contract.

- [ ] **Step 2: Run RED**

Run: `mvn "-Dtest=AskCommandHandlerTest,TodayCommandHandlerTest,WeightCommandHandlerTest,NoteCommandHandlerTest" test`

Expected: mismatch tests fail because current handlers send/publish/read state for a different chat.

- [ ] **Step 3: Add minimal fail-closed checks**

In `AskCommandHandler` and `TodayCommandHandler`, immediately after successful lookup:

```java
if (!chatId.equals(userOpt.get().getChatId())) {
    return;
}
```

In `WeightCommandHandler` and `NoteCommandHandler`, move the state read below repository lookup and this same equality check in `handle`. Their `canHandle` methods must avoid `stateUseCase.getState(chatId)` for an unlinked/mismatched user: resolve sender first, return `false` for no matching entity, then inspect state only for a matching private chat. Keep no new shared authorization abstraction; the two-line guard is deliberately local and explicit.

- [ ] **Step 4: Run GREEN and normal unit gate**

Run: `mvn "-Dtest=AskCommandHandlerTest,TodayCommandHandlerTest,WeightCommandHandlerTest,NoteCommandHandlerTest" test`

Expected: PASS.

Run: `mvn test`

Expected: PASS with no provider/Telegram network calls.

- [ ] **Step 5: Commit**

```text
fix: restrict Telegram commands to linked private chat
```

### Task 3: Private-only start and link commands

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/StartCommandHandler.java`
- Modify: `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/LinkCommandHandler.java`
- Create: `src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/StartCommandHandlerTest.java`
- Modify: `src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/LinkCommandHandlerTest.java`

**Interfaces:**
- Consumes: private-chat update dispatch from Task 1.
- Produces: direct handler calls also fail closed for non-private updates.

- [ ] **Step 1: Add direct-handler non-private tests**

Mock a non-private `message.getChat().isUserChat()` and call each handler directly. For `/start`, assert `telegramUserRepository.existsById` and `botService` receive no interactions. For `/link`, assert no code-manager interaction, no repository save, and no bot send.

Add a positive link assertion that a private update persists `TelegramUserEntity.chatId` equal to its incoming chat ID.

- [ ] **Step 2: Run RED**

Run: `mvn "-Dtest=StartCommandHandlerTest,LinkCommandHandlerTest" test`

Expected: non-private direct-handler tests fail because handlers currently execute regardless of chat type.

- [ ] **Step 3: Add minimal direct-handler guards**

At the top of `handle` in both handlers, after obtaining the message and before `chatId`, sender, code, or repository access:

```java
if (update.getMessage().getChat() == null
        || !Boolean.TRUE.equals(update.getMessage().getChat().isUserChat())) {
    return;
}
```

Do not add a reply for rejected messages.

- [ ] **Step 4: Run GREEN and full unit gate**

Run: `mvn "-Dtest=StartCommandHandlerTest,LinkCommandHandlerTest" test`

Expected: PASS.

Run: `mvn test`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
fix: restrict Telegram linking to private chats
```

### Task 4: Security verification

**Files:**
- Modify only if an uncovered acceptance criterion is found during verification.

- [ ] **Step 1: Search all handler sends/events for bypasses**

Run: `rg -n "sendMessage\\(|publishEvent\\(|updateState\\(|clearState\\(" src/main/java/com/fit/fitnessapp/telegram/application/service/handlers`

Expected: every personal path is either protected by Task 1 dispatch plus linked-chat authorization, or is an explicitly private-only `/start` or `/link` path.

- [ ] **Step 2: Run gates**

Run: `mvn test`

Expected: BUILD SUCCESS.

Run: `mvn test -Parchitecture`

Expected: BUILD SUCCESS.

- [ ] **Step 3: Inspect logging safety**

Run: `rg -n "getText\\(\\)|message text|question|content" src/main/java/com/fit/fitnessapp/telegram`

Expected: no new log statement includes raw Telegram text; any existing finding must be investigated before completion.

- [ ] **Step 4: Commit only corrective changes**

Do not create an empty verification commit. If a correction is necessary, commit it with a message that names the protected behavior.
