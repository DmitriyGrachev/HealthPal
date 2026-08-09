---
type: workflow
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../src/main/java/com/fit/fitnessapp/ai/AiController.java
  - ../../src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java
  - ../../src/main/resources/ai/prompts/daily-insight-v1.md
  - ../../src/test/java/com/fit/fitnessapp/ai/AiControllerTest.java
  - https://github.com/DmitriyGrachev/HealthPal/issues/23
tags:
  - fitnessapp
  - workflow
  - daily-insight
  - ai
---

# Daily Insight Workflow

## What It Knows

Daily insight behavior lives primarily in the AI module and uses structured prompt resources. The API can return a typed pending response when today's insight is missing and can accept daily insight generation for a target date.

Issue #23 connects daily insight with nutrition sync and end-of-day behavior. The likely missing work is orchestration: sync nutrition, generate daily insight, persist it, and notify the user through Telegram if appropriate.

## Evidence

- `src/main/java/com/fit/fitnessapp/ai/AiController.java`
- `src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java`
- `src/main/resources/ai/prompts/daily-insight-v1.md`
- `src/test/java/com/fit/fitnessapp/ai/AiControllerTest.java`
- `src/test/java/com/fit/fitnessapp/ai/AiPromptRendererTest.java`
- https://github.com/DmitriyGrachev/HealthPal/issues/23
- [[ai]]
- [[nutrition-sync]]
- [[telegram]]

## Contradictions

- Issue #23 is titled as an end-of-day reminder, but its body mentions `/api/v1/nutrition/sync/today` scheduling. The real requirement likely spans both nutrition sync and AI insight generation.

## Open Questions

- Should end-of-day daily insight be scheduled in AI, nutrition, analytics, or a dedicated orchestration service?
- Should generation skip when no nutrition data exists for the target date?
- Should Telegram notification be mandatory after insight generation or only when the user linked Telegram?

## Next Actions

- Define the end-of-day sequence before implementing issue #23.
- Keep prompt rendering tests separate from scheduler/orchestration tests.
- Use events for cross-module notification instead of direct Telegram coupling.
