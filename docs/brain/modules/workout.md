---
type: module
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../src/main/java/com/fit/fitnessapp/workout/
  - ../../src/test/java/com/fit/fitnessapp/workout/
  - ../../src/test/java/com/fit/fitnessapp/jdbc/workout/
  - https://github.com/DmitriyGrachev/HealthPal/issues/2
  - https://github.com/DmitriyGrachev/HealthPal/issues/9
  - https://github.com/DmitriyGrachev/HealthPal/issues/10
  - https://github.com/DmitriyGrachev/HealthPal/issues/11
tags:
  - fitnessapp
  - module
  - workout
---

# Workout Module

## What It Knows

Workout owns Jefit import, parser warnings, workout persistence, and read-side analytics queries. It has a small CQRS-style split: write behavior uses JPA persistence while read behavior has JDBC query adapters for reporting and analytics.

`WorkoutImportService` imports parsed sessions and persists them through `WorkoutPersistencePort.saveAll`. Parser observability exists through import warnings and parser tests.

## Evidence

- `src/main/java/com/fit/fitnessapp/workout/application/service/WorkoutImportService.java`
- `src/main/java/com/fit/fitnessapp/workout/application/port/out/WorkoutPersistencePort.java`
- `src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/WorkoutPersistenceAdapter.java`
- `src/main/java/com/fit/fitnessapp/workout/adapter/out/parser/JefitCsvParserAdapter.java`
- `src/main/java/com/fit/fitnessapp/workout/adapter/out/WorkoutJdbcQueryAdapter.java`
- `src/test/java/com/fit/fitnessapp/workout/JefitCsvParserAdapterTest.java`
- `src/test/java/com/fit/fitnessapp/workout/WorkoutImportServiceTest.java`
- `src/test/java/com/fit/fitnessapp/jdbc/workout/WorkoutJdbcQueryAdapterSqlTest.java`
- [[testing-strategy]]

## Contradictions

- Issue #11 warns against naive per-row persistence. Current import flow already passes parsed sessions to `saveAll`, so the issue needs verification before optimization work.
- Issue #2 asks about OpenCSV or Apache Commons CSV, but current parser behavior is covered by tests and warnings; replacement should be justified by a real parsing failure or maintainability gain.

## Open Questions

- Does workout import need stronger idempotency guarantees beyond the existing Done card #9?
- Is custom Jefit parsing still painful enough to justify adopting a CSV library?

## Next Actions

- Treat parser-library replacement as Later unless current parser tests expose a bug.
- Keep SQL alias tests around JDBC query changes.
- Add PostgreSQL integration coverage only when persistence behavior depends on database-specific semantics.
