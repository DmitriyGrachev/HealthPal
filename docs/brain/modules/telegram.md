---
type: module
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../AGENTS.md
  - ../../BACKLOG.md
  - ../../src/main/java/com/fit/fitnessapp/telegram/
  - ../../src/test/java/com/fit/fitnessapp/telegram/
tags:
  - fitnessapp
  - module
  - telegram
---

# Telegram Module

## What It Knows

Telegram owns bot update handling, command handlers, conversation state, user linking, rate-limited ask flow, and inbound/outbound event edges to other modules. It is privacy-sensitive because raw chat text and user messages can contain personal data.

Handlers should publish events or call public APIs rather than reaching into other modules' internal persistence.

## Evidence

- `src/main/java/com/fit/fitnessapp/telegram/adapter/in/TelegramUpdateHandler.java`
- `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandler.java`
- `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/WeightCommandHandler.java`
- `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/NoteCommandHandler.java`
- `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/LinkCommandHandler.java`
- `src/main/java/com/fit/fitnessapp/telegram/adapter/in/TelegramAiResponseListener.java`
- `src/main/java/com/fit/fitnessapp/telegram/adapter/in/TelegramNotificationListener.java`
- `src/test/java/com/fit/fitnessapp/telegram/adapter/in/TelegramUpdateHandlerPrivacyLoggingTest.java`
- `src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandlerTest.java`
- [[telegram-ask]]
- [[security-and-privacy]]

## Contradictions

- Previous backlog security work removed raw message logging. Any new logging around handlers must preserve that privacy posture.

## Open Questions

- Which Telegram flows should be fully event-driven versus direct use-case calls?
- Should user-facing Telegram messages be snapshot-tested to prevent regressions?

## Next Actions

- Never log raw Telegram message text.
- Keep `/ask` rate limiting and link-code brute-force protection covered by tests.
- Use event/listener boundaries for cross-module behavior where possible.
