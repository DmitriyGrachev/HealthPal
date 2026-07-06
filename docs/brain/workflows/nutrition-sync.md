---
type: workflow
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../src/main/java/com/fit/fitnessapp/nutrition/adapter/in/web/NutritionController.java
  - ../../src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionService.java
  - ../../src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionSyncScheduler.java
  - ../../src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/NutritionPersistenceAdapter.java
  - https://github.com/DmitriyGrachev/HealthPal/issues/4
  - https://github.com/DmitriyGrachev/HealthPal/issues/23
tags:
  - fitnessapp
  - workflow
  - nutrition
  - fatsecret
---

# Nutrition Sync Workflow

## What It Knows

Manual nutrition sync endpoints include `/api/v1/nutrition/sync/today` and `/api/v1/nutrition/sync/current-month`. `NutritionService` resolves the current user's FatSecret token before calling FatSecret through `FatSecretApiPort`.

Day persistence is idempotent at the adapter layer: aggregates are updated and food entries are synchronized. A scheduled nutrition sync entry point exists through `NutritionSyncScheduler`.

## Evidence

- `src/main/java/com/fit/fitnessapp/nutrition/adapter/in/web/NutritionController.java`
- `src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionService.java`
- `src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionSyncScheduler.java`
- `src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/NutritionPersistenceAdapter.java`
- `src/test/java/com/fit/fitnessapp/nutrition/NutritionControllerSyncTest.java`
- `src/test/java/com/fit/fitnessapp/nutrition/NutritionServicePrivacyLoggingTest.java`
- https://github.com/DmitriyGrachev/HealthPal/issues/4
- [[nutrition]]
- [[daily-insight]]

## Contradictions

- Issue #4 asks for initial load plus scheduled tail upsert. Current code has manual day/month sync, a scheduler, and idempotent persistence, but the exact initial-load and tail-window semantics are not proven by this page.

## Open Questions

- Does the system need a historical initial backfill separate from current-month sync?
- Should daily scheduled sync process `today - 2` through today before daily insight generation?
- Should sync endpoints return accepted only when work is asynchronous?

## Next Actions

- Audit current scheduler timing and sync range against issue #4.
- Decide whether [[daily-insight]] should trigger after sync completion or be scheduled independently.
- Use privacy-safe logging around all nutrition and FatSecret payload handling.
