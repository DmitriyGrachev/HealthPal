---
type: module
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../src/main/java/com/fit/fitnessapp/ai/
  - ../../src/main/resources/ai/prompts/
  - ../../src/test/java/com/fit/fitnessapp/ai/
  - https://github.com/DmitriyGrachev/HealthPal/issues/16
  - https://github.com/DmitriyGrachev/HealthPal/issues/23
  - https://github.com/DmitriyGrachev/HealthPal/issues/27
tags:
  - fitnessapp
  - module
  - ai
---

# AI Module

## What It Knows

AI owns insight generation, prompt rendering, model routing, rate limiting, provider adapters, and event listeners for reports and Telegram questions. Provider-specific behavior should stay behind module abstractions and tests must not call real OpenRouter or Gemini services.

Large prompts live as versioned resources under `src/main/resources/ai/prompts/`. `MoeOrchestrator` and `SmartAiRouter` coordinate task routing and fallback behavior.

## Evidence

- `src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java`
- `src/main/java/com/fit/fitnessapp/ai/MoeOrchestrator.java`
- `src/main/java/com/fit/fitnessapp/ai/SmartAiRouter.java`
- `src/main/java/com/fit/fitnessapp/ai/AiPromptRenderer.java`
- `src/main/java/com/fit/fitnessapp/ai/application/port/out/AiModelPort.java`
- `src/main/resources/ai/prompts/daily-insight-v1.md`
- `src/main/resources/ai/prompts/telegram-ask-v1.md`
- `src/test/java/com/fit/fitnessapp/ai/AiPromptRendererTest.java`
- `src/test/java/com/fit/fitnessapp/ai/SmartAiRouterTest.java`
- `src/test/java/com/fit/fitnessapp/ai/AiControllerTest.java`
- [[daily-insight]]
- [[telegram-ask]]

## Contradictions

- Issue #27 asked to remove hardcoded AI model names and is marked Done; current work should not reopen that without fresh evidence.
- Issue #23 asks for end-of-day insight behavior, but the current code already has generation and retrieval endpoints. The missing piece may be orchestration/scheduling rather than AI response structure.

## Open Questions

- Should the end-of-day insight flow be owned by AI, nutrition, analytics, or a dedicated orchestration service?
- Should daily insight generation trigger Telegram notification by event after persistence?

## Next Actions

- Verify [[daily-insight]] before adding scheduling.
- Keep prompt changes separate from orchestration refactors.
- Mock all AI providers in tests and prefer prompt rendering tests for template changes.
