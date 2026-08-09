---
type: module
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../CONTEXT.md
  - ../../src/main/java/com/fit/fitnessapp/analytics/
  - ../../src/test/java/com/fit/fitnessapp/analytics/
tags:
  - fitnessapp
  - module
  - analytics
---

# Analytics Module

## What It Knows

Analytics owns weekly and monthly report orchestration. Its scheduled jobs are operational batch workflows that can iterate across users and publish report-request events for AI handling.

Security rules distinguish all-user batch report endpoints from personal workflows. Batch actions need admin-style protection.

## Evidence

- `src/main/java/com/fit/fitnessapp/analytics/application/WeeklyReportOrchestrator.java`
- `src/main/java/com/fit/fitnessapp/analytics/application/WeeklyReportTransactionService.java`
- `src/main/java/com/fit/fitnessapp/analytics/application/MonthlyReportOrchestrator.java`
- `src/main/java/com/fit/fitnessapp/analytics/application/MonthlyReportTransactionService.java`
- `src/main/java/com/fit/fitnessapp/analytics/port/in/WeeklyReportController.java`
- `src/main/java/com/fit/fitnessapp/analytics/port/in/MonthlyReportController.java`
- `src/test/java/com/fit/fitnessapp/analytics/MonthlyReportOrchestratorTest.java`
- [[ai]]
- [[spring-modulith]]
- [[security-and-privacy]]

## Contradictions

- No active open GitHub issue currently appears to target analytics directly; related Done cards describe previous analytics and monthly insight work.

## Open Questions

- Should report scheduling be configurable per environment?
- Should weekly and monthly report flows share a public workflow page later?

## Next Actions

- Keep scheduled report date-range behavior covered by unit tests.
- Verify security gates before exposing or changing all-user report endpoints.
- Use stable events for AI report generation.
