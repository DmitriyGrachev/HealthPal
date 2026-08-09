# ADR 0014: Separate AI Report Workflow Boundaries

## Context

`FitnessAiService` had accumulated event handling, durable-job scheduling, weekly/monthly prompt construction, provider calls, validation, persistence, and snapshot hashing. That made transaction and provider boundaries difficult to review and encouraged direct repository dependencies in application code.

## Decision

Keep `FitnessAiService` as the event/job façade. Run weekly and monthly report generation in separate `WeeklyReportService` and `MonthlyReportService` application services. Shared snapshot fingerprints live in `ReportSnapshotHasher`, and report reads use the `AiInsightPort` output contract. Existing characterization tests continue to exercise freshness, validation, persistence, and event publication behavior.

## Consequences

- Event orchestration and report execution can be tested independently.
- Provider calls remain behind `MoeOrchestrator`; persistence is accessed through an application port for report reads.
- Further decomposition of daily insight and prompt/context helpers can proceed without changing report contracts.
