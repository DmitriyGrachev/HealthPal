# FND-009 Telegram Private Chat Boundary Design

## Goal

Prevent personal fitness data, account-link status, and user-triggered work
from appearing in Telegram groups or channels. A Telegram identity is valid
only in its linked private chat.

## Boundary Rule

An update is eligible for command dispatch only when it has a message whose
chat is a Telegram private/user chat. `TelegramUpdateHandler` applies this
rule before selecting a `CommandHandler`.

For a non-private group, supergroup, or channel update, the application does
not dispatch a handler and performs no bot send, event publication, database
write, or conversation-state operation. It may log only non-sensitive
metadata such as chat type and handler-dispatch status; it must not log raw
message text.

## Linked Identity Rule

Personal handlers are `/ask`, `/today`, `/weight`, `/note`, and continuations
of the weight/note state machines. After resolving `TelegramUserEntity` by
sender Telegram ID, they require:

```text
incoming chat ID == stored TelegramUserEntity.chatId
```

If the IDs differ, the handler returns without sending a message, publishing
an event, writing state, or writing any domain data. This fail-closed behavior
does not disclose whether the sender is linked or which private chat is
linked.

The check is performed before a rate-limit token is consumed and before a
state-machine read or write. Stateful handlers therefore never resume a
conversation from an unlinked private chat.

## Link and Start Commands

`/start` and `/link` are eligible only in private chats because their replies
reveal linking status and `/link` establishes the stored chat ownership.
`/link` persists the private incoming `chatId`; the link-code and rate-limit
behavior otherwise remains unchanged.

## Design Alternatives Rejected

- Repeating chat-type and chat-ID checks in each handler would duplicate a
  security boundary and make future commands easy to miss.
- Querying persistence from `TelegramUpdateHandler` would mix transport
  dispatch with per-command identity authorization. The handler owns the
  universal private-chat rule; personal handlers own linked-user matching.

## Tests

Unit tests use mocked Telegram `Update`, `Message`, and `Chat` objects.

- `TelegramUpdateHandler` does not invoke any handler for group, supergroup,
  or channel messages and does not expose raw text in logs.
- Each personal handler rejects a private update whose sender is linked to a
  different stored chat ID with no bot send/event/state mutation/rate-limit
  consumption.
- Existing matching private-chat behavior remains covered.
- `/start` and `/link` cannot run in a group; a valid private `/link` saves
  the private chat ID.

The fast verification gate is the focused Telegram handler test set followed
by `mvn test`. No Docker/Testcontainers check is required for this in-memory
handler security boundary.

## Scope

This change does not implement durable Telegram link codes (`FND-008`),
delivery outbox/retry (`FND-010`), or centralized command parsing (`FND-024`).
It makes the currently stored Telegram identity fail closed until those later
P0/P1 improvements replace their underlying storage and workflow.
