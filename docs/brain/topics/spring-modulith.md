---
type: topic
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../pom.xml
  - ../../src/test/java/com/fit/fitnessapp/module/ModuleArchitectureTest.java
  - ../../src/main/java/com/fit/fitnessapp/
  - https://github.com/DmitriyGrachev/HealthPal/issues/7
  - https://github.com/DmitriyGrachev/HealthPal/issues/15
tags:
  - fitnessapp
  - spring-modulith
  - architecture
---

# Spring Modulith

## What It Knows

FitnessApp uses Spring Modulith dependencies and an architecture profile test. Module boundaries should avoid internal package imports across business modules. Cross-module communication should use public APIs, stable event packages, or ports.

Events and listeners exist across AI, memory, nutrition, telegram, auth, and analytics flows.

## Evidence

- `pom.xml`
- `src/test/java/com/fit/fitnessapp/module/ModuleArchitectureTest.java`
- `src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java`
- `src/main/java/com/fit/fitnessapp/memory/application/service/MemoryEventListener.java`
- `src/main/java/com/fit/fitnessapp/telegram/adapter/in/TelegramNotificationListener.java`
- `src/main/java/com/fit/fitnessapp/nutrition/adapter/in/TelegramWeightListener.java`
- `src/main/java/com/fit/fitnessapp/analytics/application/WeeklyReportTransactionService.java`
- [[testing-strategy]]
- [[github-project-triage]]

## Contradictions

- Issue #7 asks for module interaction and transactional outbox. Current code has Spring Modulith dependencies, events, listeners, and architecture tests, but the exact outbox expectation needs verification.

## Open Questions

- Does issue #7 require Spring Modulith JDBC event publication/outbox semantics beyond the current listeners?
- Which event classes should be exposed as stable public API?

## Next Actions

- Run `mvn test -Parchitecture` for module-boundary work.
- Prefer public API packages, stable event packages, or ports for cross-module behavior.
- Verify transactional outbox needs before adding infrastructure.
