---
type: module
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../CONTEXT.md
  - ../../src/main/java/com/fit/fitnessapp/nutrition/
  - ../../src/test/java/com/fit/fitnessapp/nutrition/
  - https://github.com/DmitriyGrachev/HealthPal/issues/1
  - https://github.com/DmitriyGrachev/HealthPal/issues/4
  - https://github.com/DmitriyGrachev/HealthPal/issues/8
  - https://github.com/DmitriyGrachev/HealthPal/issues/14
  - https://github.com/DmitriyGrachev/HealthPal/issues/23
tags:
  - fitnessapp
  - module
  - nutrition
  - fatsecret
---

# Nutrition Module

## What It Knows

Nutrition owns FatSecret connection, token persistence, day and month sync, profile and weight history behavior, nutrition query endpoints, and public nutrition stats APIs. It is the main boundary around private nutrition data.

`NutritionService` resolves the user's FatSecret token before syncing day or month data. `NutritionPersistenceAdapter` keeps day persistence idempotent by updating day aggregates and synchronizing food entries. `FatSecretProfileService` handles latest weight, weight history, exercise entries, and FatSecret weight update behavior.

Scheduled entry points exist for nutrition sync and FatSecret profile sync.

## Evidence

- `src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionService.java`
- `src/main/java/com/fit/fitnessapp/nutrition/application/service/NutritionSyncScheduler.java`
- `src/main/java/com/fit/fitnessapp/nutrition/application/service/FatSecretProfileService.java`
- `src/main/java/com/fit/fitnessapp/nutrition/application/service/FatSecretProfileSyncService.java`
- `src/main/java/com/fit/fitnessapp/nutrition/application/port/out/FatSecretApiPort.java`
- `src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/NutritionPersistenceAdapter.java`
- `src/main/java/com/fit/fitnessapp/nutrition/adapter/in/web/NutritionController.java`
- `src/test/java/com/fit/fitnessapp/nutrition/NutritionControllerSyncTest.java`
- `src/test/java/com/fit/fitnessapp/nutrition/NutritionServicePrivacyLoggingTest.java`
- `src/test/java/com/fit/fitnessapp/nutrition/FatSecretProfileServicePrivacyLoggingTest.java`
- [[nutrition-sync]]
- [[fatsecret-integration]]

## Contradictions

- GitHub issues #1, #8, and #14 still describe FatSecret controller/service/repository/profile work, but current code already contains many of those pieces.
- Issue #4 asks for initial load plus scheduled tail upsert. Current code has day/month sync, idempotent persistence, and a scheduler, but historical backfill and exact tail semantics still need verification.

## Open Questions

- Does the current `NutritionSyncScheduler` match the product expectation for end-of-day or tail sync timing?
- Is there a historical initial-load flow, or only day/month sync?
- Should FatSecret profile be the source of truth for all weight goals, or only for weight history and current weight?

## Next Actions

- Verify [[nutrition-sync]] against issue #4 before implementing more sync logic.
- Deduplicate or close #1, #8, and #14 after checking FatSecret profile behavior.
- Keep privacy logging tests close to any FatSecret adapter or nutrition service logging change.
