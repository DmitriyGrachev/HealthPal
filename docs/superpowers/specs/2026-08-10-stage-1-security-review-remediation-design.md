# Stage 1 Security Review Remediation Design

## Goal

Close the three validated Stage 1 security findings without broadening the work into a repository-wide lifecycle-generation system:

1. unlink must revoke queued private Telegram delivery;
2. user-owned Spring Modulith publications must not survive account deletion;
3. delayed Telegram work must not recreate private durable state after unlink or account deletion.

The change must preserve anonymous Telegram replies needed before linking, preserve malformed and system event publications that have no usable user owner, avoid real provider calls in tests, and keep the current module boundaries green.

## Chosen Approach

Use database-enforced ownership for durable rows and application-level active-link checks at Telegram ingress and egress.

PostgreSQL foreign keys are the final invariant for concurrent commits: a user- or link-owned durable write either commits while its owner exists or fails/no-ops after revocation. Application checks provide clean control flow and prevent expected stale work from surfacing as persistence errors. Delivery workers also revalidate the current link immediately before Telegram network I/O.

This is narrower than introducing a lifecycle generation acquired by every user workflow, while being stronger than repeated best-effort cleanup queries.

## Database Changes

Add an immutable Flyway migration `V27__own_durable_user_work.sql`.

### Event Publications

Add a safe PostgreSQL extraction function for `serialized_event.userId`. It returns `NULL` for malformed JSON, missing IDs, non-numeric IDs, non-positive IDs, or numeric overflow rather than raising an exception.

Add `event_publication.user_id BIGINT` as a stored generated column using that function. Before adding the FK, delete publications whose extracted owner no longer exists. Add:

- an index on non-null `user_id`;
- `FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE`.

Malformed events and system events without `userId` remain ownerless and are not deleted by user lifecycle cleanup. User-owned publications become cascade-owned regardless of whether Spring Modulith inserts them before or concurrently with account deletion.

### Telegram Ownership

Add `user_id` to:

- `telegram_delivery_outbox` as nullable, because pre-link and link-status messages are intentionally anonymous;
- `conversation_state` as non-null after safe backfill;
- `conversation_history` as non-null after safe backfill.

Add unique constraints on `telegram_users(user_id, chat_id)` and `telegram_users(chat_id)`, then add composite foreign keys from owned Telegram rows to the owner pair with `ON DELETE CASCADE`. A private Telegram chat is single-owner.

Legacy migration rules:

- rows with exactly one matching link are backfilled;
- child rows whose `chat_id` maps to multiple links are deleted because their owner cannot be established safely;
- duplicate legacy links retain the newest deterministic link (`linked_at`, then `telegram_id`) before `UNIQUE(chat_id)` is enforced;
- orphan conversation state/history rows are deleted;
- pending or sending outbox rows without one unambiguous active owner are deleted, because the legacy schema cannot distinguish revoked private work from pre-link work;
- terminal `SENT`/`FAILED` outbox rows without an attributable owner remain anonymous audit records and are never retried;
- existing owned outbox rows are backfilled and become revocable.

The migration does not mutate earlier Flyway files.

The Stage 1 retention follow-up adds `V28__purge_terminal_ownerless_telegram_outbox.sql`. It removes legacy `SENT` and `FAILED` rows whose `user_id` is null, plus ownerless `PENDING` rows that have already exhausted `max_attempts` and can no longer be claimed. Retryable ownerless `PENDING`/`SENDING` rows remain recoverable delivery state, while owned terminal rows remain linked and continue to be removed by the existing cascade.

## Telegram Application Flow

`TelegramBotService` retains the existing anonymous `sendMessage` and `enqueueMessage` operations for pre-link communication. It gains an owned enqueue operation:

```java
boolean enqueueOwnedMessage(Long userId, Long chatId, String text)
```

The operation inserts chunks with `user_id` only through an active `(user_id, chat_id)` link. It returns `false` when the link is stale or revoked and performs no network I/O.

Private paths use the owned operation:

- `TelegramNotificationListener` for generated insights;
- `TelegramAiResponseListener` for delayed AI responses.
- content-bearing Note and Weight confirmations that contain the user's note or weight.

AI responses become durable queued deliveries instead of immediate sends. This prevents stale events from creating an ownerless `SENDING` row or performing direct network I/O after deletion.

The retry worker:

1. marks owned rows with no matching active link as revoked/non-deliverable;
2. claims only anonymous rows or owned rows with a matching active link;
3. revalidates an owned row immediately before Telegram API execution;
4. deletes or marks the row revoked when authorization disappeared.

The final check minimizes the unavoidable gap between database authorization and an external network request. Absolute cross-process atomicity would require holding a database/distributed lock across Telegram I/O, which is intentionally excluded because it would reintroduce external I/O inside a transaction.

Anonymous rows exist only while delivery remains recoverable. A successful anonymous send, a permanent anonymous failure, or exhaustion of its retry budget physically deletes the row. Recovery also deletes stale anonymous `SENDING` claims that have exhausted `max_attempts`. The equivalent exhausted owned claim is retained as an owner-bound `FAILED` row with `CLAIM_TIMEOUT_MAX_ATTEMPTS`, preserving owned audit state and cascade revocation.

## Unlink Flow

Introduce a focused Telegram application service for revocation. Within one transaction it:

1. locks the active `telegram_users` row for the user;
2. deletes every outbox row for the locked `chat_id`, including ownerless rows;
3. deletes the link;
4. relies on composite FK cascades to delete conversation state/history and any remaining owned outbox rows.

`TelegramLinkController` delegates unlinking to this service. Concurrent owned enqueue/state writes lock or reference the same link, so the final committed state cannot contain owned durable work without the link.

Unlink revokes durable deliveries that have not passed their final active-link check. It does not provide strict cross-process atomicity with Telegram: a delivery already authorized immediately before unlink may finish its external API call after unlink completes. The service therefore does not hold a database transaction or row lock across Telegram network I/O, and no distributed lock, lease, lifecycle generation, or new broker is introduced. This accepted in-flight window is the explicit contract; the worker's final active-link check remains the practical revocation boundary.

## Conversation State

Conversation state writes become explicitly user-owned. The use-case methods accept both `userId` and `chatId`, and the two linked command handlers pass the already resolved owner.

Before save, the service locks and verifies the active `(userId, chatId)` link. A stale command is a no-op. The composite FK is the final race protection if unlink wins after the application check.

Read and clear operations may continue using `chatId`, because they do not create private state. Existing state entities gain a `userId` field matching the migration.

## Account Deletion And Event Cleanup

The existing module-owned lifecycle coordinator remains unchanged in shape. Telegram cascades and event-publication ownership make its one-shot cleanup safe against late durable writes.

The event publication participant remains as an explicit cleanup for legacy/system compatibility, but user-owned publications are also protected by the FK. No global in-memory lock is introduced.

## Version-Aware Insight Memory Replay

Every production `InsightGeneratedEvent` carries the source `snapshot_hash`. Before any AI egress guard or vector-provider operation, the memory listener verifies that the user still exists, the event version is nonblank, and the current AI-owned insight source has the exact same hash. Missing or stale versions are safe no-ops.

Generated and deleted insight replay share a PostgreSQL transaction-scoped advisory lock derived from `(userId, insightType, date)`. AI source save/delete and source comparison acquire that lock before touching the natural key. This serializes an absent-source delete check with concurrent recreation, closing the PostgreSQL gap where `SELECT FOR UPDATE` cannot lock a nonexistent row. A stale delete therefore either finishes before recreation or observes the recreated source and becomes a no-op; it cannot delete the new projection. Hash collisions can only over-serialize unrelated insight keys, not violate correctness.

## Error Handling And Logging

Stale owned Telegram work is expected and must not produce raw message content in logs. Log only identifiers, operation type, status, and stable codes such as `TELEGRAM_LINK_REVOKED`.

Owned writes lock the active link for the duration of their short database transaction. A missing link produces a safe no-op; unexpected persistence failures still propagate rather than being hidden.

No Telegram message, AI response, note content, token, or provider secret is logged.

## Tests

Use PostgreSQL/Testcontainers for migration, FK, cascade, and concurrency behavior. Use Mockito unit tests only for routing decisions and network-call suppression.

Required tests:

1. queue an owned private notification, unlink, run the worker, assert zero Telegram API calls and no deliverable row;
2. race owned enqueue against unlink, assert no active link and no owned outbox row;
3. release a delayed AI response after account deletion, assert no send and no outbox row;
4. attempt state creation after unlink/account deletion, assert no row;
5. overlap a user-owned publication with account deletion, assert no surviving personal publication;
6. verify V27 event owner extraction, malformed JSON preservation, orphan cleanup, and cascade deletion;
7. verify Telegram backfill, ambiguous/orphan cleanup, composite FK enforcement, and cascade revocation;
8. preserve anonymous pre-link delivery behavior;
9. preserve another user's link, state, history, and outbox rows;
10. reject duplicate post-migration ownership of one private `chat_id`;
11. unlink both queued and already-sent owned private delivery rows.

Required final gates:

```text
mvn test
mvn test -Parchitecture
mvn verify -Pintegration
git diff --check
.codex/hooks/privacy-scan.ps1
npx graphify hook-rebuild
```

Tests must not call real Telegram, OpenRouter, Gemini, FatSecret, or embedding providers.

## Scope Boundaries

Included:

- the three reportable findings;
- schema ownership needed to close their races;
- focused tests and documentation updates required by the changed behavior.

Excluded:

- a global lifecycle generation used by every module workflow;
- holding database transactions or locks across external Telegram network calls;
- redesigning all bot responses as user-owned messages;
- unrelated stale replay, AI budget, or prompt-hardening candidates rejected by the supplied review;
- commit, stage, push, branch creation, or pull request creation.

## Acceptance Criteria

The work is complete when all three supplied findings have regression tests that fail before their corresponding fixes, all focused and full gates pass, Graphify is rebuilt, privacy scanning is clean, and the worktree remains uncommitted and unstaged.
