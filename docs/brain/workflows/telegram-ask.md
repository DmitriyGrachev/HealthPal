---
type: workflow
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandler.java
  - ../../src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java
  - ../../src/main/resources/ai/prompts/telegram-ask-v1.md
  - ../../src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandlerTest.java
tags:
  - fitnessapp
  - workflow
  - telegram
  - ai
---

# Telegram Ask Workflow

## What It Knows

Telegram `/ask` is rate-limited before publishing an AI request event. AI handles the question through prompt/template/provider infrastructure, and Telegram sends a response through listener flow.

The workflow is privacy-sensitive because Telegram input may contain user notes, nutrition details, or other personal content. Tests should mock AI behavior and should not call Telegram or provider services.

## Evidence

- `src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandler.java`
- `src/main/java/com/fit/fitnessapp/telegram/adapter/in/TelegramAiResponseListener.java`
- `src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java`
- `src/main/resources/ai/prompts/telegram-ask-v1.md`
- `src/test/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandlerTest.java`
- `src/test/java/com/fit/fitnessapp/telegram/adapter/in/TelegramUpdateHandlerPrivacyLoggingTest.java`
- [[telegram]]
- [[ai]]
- [[security-and-privacy]]

## Contradictions

- No current open GitHub issue specifically targets `/ask`, but previous security backlog work hardened rate limits and privacy logging. Those protections are still part of the workflow contract.

## Open Questions

- Should `/ask` responses cite which user context was used, without exposing private raw details?
- Should failed AI requests produce a Telegram-safe fallback message with structured error reason?

## Next Actions

- Preserve rate limiting before event publication.
- Avoid raw Telegram text in logs and wiki examples.
- Keep provider calls mocked in handler and AI workflow tests.
