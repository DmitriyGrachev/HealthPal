---
name: fitness-ai-prompts
description: Guides FitnessApp AI prompt and provider workflow changes, including prompt extraction, rendering tests, SmartAiRouter behavior, OpenRouter/Gemini adapters, pgvector memory, and privacy-safe AI context. Use when editing FitnessAiService prompts, creating src/main/resources/ai/prompts templates, testing prompt rendering, or changing AI routing and fallback behavior.
---

# Fitness AI Prompts

## Workflow

1. Locate the current prompt or provider behavior in the `ai` and `memory` modules.
2. Separate prompt text changes from orchestration or adapter behavior changes when practical.
3. For large prompts, move text into versioned resources under `src/main/resources/ai/prompts/`.
4. Add rendering tests with fixture nutrition, workout, memory, or user-note context.
5. Mock all provider calls. Never call real OpenRouter, Gemini, or embedding providers in tests.

## Prompt Resource Pattern

Use clear names such as:

- `src/main/resources/ai/prompts/daily-insight-v1.md`
- `src/main/resources/ai/prompts/weekly-report-v1.md`
- `src/main/resources/ai/prompts/monthly-report-v1.md`
- `src/main/resources/ai/prompts/telegram-ask-v1.md`

Keep version metadata in the filename or a small header. Do not mix unrelated prompt rewrites into provider adapter fixes.

## Privacy And Safety

- Do not include raw secrets, tokens, or full logs in prompts.
- Keep user-specific memory filtered by server-side user id.
- Prefer structured context objects or template variables over string concatenation.
- Ensure logs never print full prompt bodies when they include private user data.

## Provider Workflow

- Keep OpenRouter and Gemini behavior behind `AiModelPort` and router abstractions.
- Preserve fallback behavior with focused tests for auth errors, invalid requests, malformed output, and provider unavailability.
- Use Context7 or official docs when changing Spring AI APIs.

## Acceptance

Before finishing, run the prompt rendering or AI unit tests that cover the changed path, then run `mvn test` if the change affects shared AI behavior.
