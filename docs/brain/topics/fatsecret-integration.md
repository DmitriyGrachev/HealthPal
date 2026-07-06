---
type: topic
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../src/main/java/com/fit/fitnessapp/nutrition/application/port/out/FatSecretApiPort.java
  - ../../src/main/java/com/fit/fitnessapp/nutrition/adapter/out/api/FatSecretApiAdapter.java
  - ../../src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/NutritionPersistenceAdapter.java
  - ../../src/main/java/com/fit/fitnessapp/nutrition/application/service/FatSecretProfileService.java
  - https://github.com/DmitriyGrachev/HealthPal/issues/1
  - https://github.com/DmitriyGrachev/HealthPal/issues/8
  - https://github.com/DmitriyGrachev/HealthPal/issues/14
tags:
  - fitnessapp
  - fatsecret
  - integration
---

# FatSecret Integration

## What It Knows

FatSecret behavior is isolated behind nutrition abstractions such as `FatSecretApiPort`. The adapter handles OAuth-backed calls for food entries, monthly food entries, weight history, latest weight, update weight, exercises, and exercise entries.

OAuth tokens are persisted through nutrition persistence and must remain encrypted/private. FatSecret logs should record status and safe metadata, not raw response bodies or token values.

## Evidence

- `src/main/java/com/fit/fitnessapp/nutrition/application/port/out/FatSecretApiPort.java`
- `src/main/java/com/fit/fitnessapp/nutrition/adapter/out/api/FatSecretApiAdapter.java`
- `src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/NutritionPersistenceAdapter.java`
- `src/main/java/com/fit/fitnessapp/nutrition/application/service/FatSecretProfileService.java`
- `src/test/java/com/fit/fitnessapp/nutrition/FatSecretProfileServicePrivacyLoggingTest.java`
- `src/test/java/com/fit/fitnessapp/nutrition/FatSecretTokenEncryptionIntegrationTest.java`
- [[nutrition]]
- [[security-and-privacy]]

## Contradictions

- Issues #1, #8, and #14 still ask for FatSecret controller/service/repository/profile work, but code evidence shows those areas partially or fully exist.

## Open Questions

- Should FatSecret profile data become source of truth for target weight goals, or only for current/observed weight?
- Does profile sync need step/activity support, or should Apple activity remain out of scope?

## Next Actions

- Deduplicate #8 and #14 before adding profile work.
- Verify token encryption tests and privacy logging tests around any adapter or persistence change.
- Keep real FatSecret calls out of automated tests.
