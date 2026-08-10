# Stage 1 Foundation Debts Design

## Goal

Close the first-stage ownership, deletion replay-safety, and AI egress privacy debts without changing public API behavior beyond adding the user AI budget to the existing personal-data export.

## Constraints

- Keep `auth` as the HTTP lifecycle owner and synchronous transaction coordinator.
- Keep persistence details and foreign-module table names inside their owning modules.
- Preserve the current export JSON fields and add only `aiUsageBudget`.
- Delete an account in one transaction and delete the auth identity last.
- Keep FatSecret disconnect limited to the OAuth connection.
- Keep Spring Modulith event retention and dependency versions unchanged.
- Do not call real OpenRouter, Gemini, Telegram, or FatSecret APIs in tests.
- Add only forward Flyway migrations; existing migrations are immutable.
- Do not commit, stage, or push this work.

## Lifecycle Architecture

Introduce a small `UserDataLifecycleParticipant` contract in the existing shared `com.fit.fitnessapp.api` named interface. A participant has a stable key, exports one module-owned fragment, deletes module-owned data, and may implement a narrowly scoped external-connection disconnect operation. The contract is a local coordination SPI, not a generic workflow framework.

Each owning module provides one participant:

- `auth`: identity metadata, roles, and User Notes export/cleanup support.
- `nutrition`: profile, weight, FatSecret days/foods, and FatSecret Connection state; owns disconnect.
- `workout`: Workout Sessions, exercises, sets, and cardio.
- `ai`: AI Insights and USER-scoped AI usage budget rows.
- `memory`: User Memory rows using the constraint-backed owner column.
- `telegram`: Telegram Link, conversation state/history, and delivery outbox; resolves the chat identifier before deleting the link.
- infrastructure/job: Durable Jobs and user-specific Spring Modulith event publications.

`UserDataLifecycleService` verifies that the User exists, invokes the participant list in stable key order, adapts the returned fragments into the existing `UserDataExportDto`, and deletes the auth identity only after all participant cleanup succeeds. The complete delete operation remains synchronous and `@Transactional`; any participant exception rolls back every write.

The service may know stable lifecycle keys and the public export contract. It must not contain persistence entities, SQL, or table names owned by other modules.

## Export Contract

The existing top-level fields remain unchanged:

`userId`, `email`, `username`, `exportedAt`, `profile`, `weightHistory`, `nutritionDays`, `foodEntries`, `workoutSessions`, `workoutExercises`, `workoutSets`, `workoutCardio`, `userNotes`, `aiInsights`, `memories`, `telegramAccount`, `conversationState`, `conversationHistory`, `telegramDeliveries`, `durableJobs`, and `fatSecretConnected`.

Add `aiUsageBudget` as a list containing only rows whose `scope_type` is `USER` and whose `scope_id` equals the exported User ID. GLOBAL rows are neither exported as personal data nor deleted.

Participant fragments use stable keys and immutable maps/lists. Missing optional module data is represented with the same empty map/list behavior as the existing endpoint.

## FatSecret Ownership

Move the remaining query for connected User IDs out of `auth.UserRepository`. `FatSecretProfileSyncService` uses the existing nutrition-owned `NutritionCommandPort.getAllConnectedUserIds()` boundary. Remove `auth.api.UserPort` and `UserAdapter` if no usages remain.

`disconnectFatSecret` stays on the auth-owned HTTP use case, but coordination reaches the nutrition participant through the shared lifecycle SPI. Only the `fatsecret_connection` row is deleted; nutrition history is preserved.

## Event Publication Cleanup

The infrastructure participant deletes both completed and incomplete publications for exactly one User. It treats `serialized_event` as JSON and compares the structured `userId` value rather than applying `LIKE` to text. Publications without that exact User ID remain untouched. No global retention behavior or Spring Modulith version changes are introduced.

## User Memory Ownership Migration

Add one new Flyway migration after the latest existing version. It:

1. Explicitly removes derived `user_memory` rows whose metadata has no numeric `user_id`, or whose numeric owner no longer exists. User Memory is derived data, so removing unusable orphan rows is safer than blocking deployment indefinitely.
2. Adds a stored generated `user_id BIGINT` column derived from `metadata->>'user_id'`, which remains compatible with Spring AI `VectorStore` writes.
3. Adds an index on `user_id`.
4. Adds a foreign key to `users(id) ON DELETE CASCADE`.

New VectorStore writes with missing, malformed, or nonexistent owners fail at the database boundary. Lifecycle queries and deletes use the generated owner column rather than repeating JSON extraction.

## Replay Safety

Add a small public auth query boundary that can answer whether a User exists. Before adding a User Memory from an old event, `MemoryEventListener` checks that the User still exists. For User Note events with a source ID, it also checks that the source note still belongs to that User. Missing owners or sources produce a successful no-op before calling `VectorStore`; arbitrary `DataIntegrityViolationException` is not swallowed.

Telegram notification consumers continue to resolve the current Telegram Link before enqueuing delivery. A deleted User has no link because the link is module-owned and removed in the account transaction, so replay completes without creating an outbox row.

This makes stale publications drain safely instead of retrying forever, while the foreign key remains the final integrity backstop.

## AI Egress Boundary

Add:

- `AiDataClass`: `PUBLIC`, `SENSITIVE`, `SECRET`.
- `ClassifiedAiPrompt`: prompt body plus explicit classification.
- `AiEgressPolicy`: the single policy check for a logical routing attempt.
- A typed policy-denial exception with a stable error code and no prompt content.

`MoeOrchestrator.route` accepts `ClassifiedAiPrompt`. It invokes `AiEgressPolicy` once, before `AiExecutionGuard.execute`. Only after successful policy validation does it unwrap the raw prompt for token estimation, budget reservation, SmartAiRouter fallback, or a direct provider call. `AiModelPort` may remain String-based behind that validated boundary.

Policy behavior:

- missing request or missing classification: deny;
- `SECRET`: always deny;
- `PUBLIC`: allow;
- `SENSITIVE`: allow only when `app.ai.allow-sensitive-external-egress` is explicitly true;
- configuration default: false.

Document the environment override in example/config documentation without reading or modifying the real `.env`.

Daily Insight, Weekly Report, Monthly Report, and Telegram AI Reply requests are all classified `SENSITIVE`. Policy denial occurs before budget reservation and does not start another provider fallback.

## Untrusted User Content

Extend `AiSafetyService` into one bounded-data mechanism for `user_question`, `user_note`, and `user_memory`:

- enforce a defined maximum input length;
- normalize/validate the allowed tag name rather than accepting arbitrary tags;
- escape closing-tag variants so content cannot terminate its boundary;
- wrap content in a clearly labelled data block that instructs the model to treat it as data, not system instructions;
- do not use regex-based semantic rewriting.

Telegram questions use `user_question`. Weekly/monthly User Notes use `user_note`. Memory content returned by `AiContextService` uses `user_memory`. Existing prompt templates and response contracts otherwise remain unchanged.

Logs may contain only User ID, task type, provider/model, classification, prompt size, status, and stable error code. Prompt bodies, notes, memory content, nutrition detail, and credentials are never logged.

## Failure Semantics

- Unknown User export/delete: fail before participant work.
- Participant export failure: fail the request; do not return a partial export.
- Participant cleanup failure: propagate and roll back the whole account transaction.
- Policy denial: typed failure before bulkhead, budget, router, or provider work.
- Missing replay owner/source: successful no-op.
- Unexpected persistence failure: propagate; do not classify it as replay-safe.

## Test Strategy

Use red-green-refactor for behavior changes.

1. Expand `UserDataLifecycleServiceIntegrationTest` into one two-User PostgreSQL scenario covering every owned category, USER and GLOBAL AI budgets, exact completed/incomplete publication cleanup, export compatibility, deletion isolation, and rollback-visible ownership behavior.
2. Add one PostgreSQL replay scenario proving a stale event cannot recreate User Memory or a Telegram delivery after deletion.
3. Add `AiEgressPolicyTest` for PUBLIC, SENSITIVE opt-out/opt-in, SECRET, and unclassified behavior.
4. Add one `MoeOrchestratorTest` proving denial happens before `AiExecutionGuard`, budget, router, and provider work.
5. Extend `AiSafetyServiceTest` for closing tags and length limits across `user_note` and `user_memory`.
6. Remove or rewrite SQL-string-mocking lifecycle unit tests; retain behavior-oriented coverage only.
7. Run focused tests, `git diff --check`, `mvn test`, `mvn test -Parchitecture`, and `mvn verify -Pintegration`.

The current integration baseline is known to fail before code changes because Testcontainers cannot find a valid Docker environment. This is environmental evidence, not an accepted final result; the final integration gate must be retried after Docker becomes available.

## Acceptance Mapping

- Module participants remove foreign persistence knowledge from `UserDataLifecycleService`.
- Nutrition owns FatSecret connection lookup and disconnect.
- Export remains compatible and includes only the User AI budget.
- Synchronous cleanup is transactional, isolated, and leaves GLOBAL budget intact.
- Event publications use exact structured matching.
- User Memory ownership is enforced by generated owner column, index, and cascading FK.
- Replay does not resurrect Memory or Telegram outbox state.
- AI egress is explicitly classified and default-deny.
- Personal content is bounded and tagged as untrusted data.
- Existing provider routing, fallbacks, timeouts, budgets, prompt templates, and response contracts remain intact after policy validation.
