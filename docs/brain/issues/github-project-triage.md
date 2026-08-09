---
type: issue-triage
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../sources/github-project-2026-07-06.md
  - https://github.com/users/DmitriyGrachev/projects/1
  - https://github.com/DmitriyGrachev/HealthPal/issues
tags:
  - fitnessapp
  - github-project
  - triage
---

# GitHub Project Triage

## What It Knows

The Project board is useful but stale. Treat card status as input, not truth. Verify each old issue against code and tests before implementation.

## Now

- #29 Fix UTF-8 encoding corruption - project hygiene and text quality issue.
- #4 Nutrition initial load and scheduled tail upsert - likely active workflow gap connected to [[nutrition-sync]].
- #13 Core module validation - partially implemented but still worth auditing across auth, nutrition, and workout.
- #23 End-of-day daily insight - product workflow connected to [[daily-insight]] and [[nutrition-sync]].

## Verify / Close

- #18 Add CI/CD - `.github/workflows/ci.yml` exists with unit, architecture, integration, and dependency hygiene jobs.
- #7 Implement module interaction - Spring Modulith dependencies, listeners, events, and architecture tests exist; verify transactional outbox expectation.
- #1 FatSecret controller/service/repository - current nutrition code contains FatSecret connection, API port, adapter, persistence entity, repository, and controller flow.
- #8 FatSecret profile as source of truth - overlaps with current FatSecret profile and weight behavior.
- #14 FatSecret profile logic - overlaps with #8 and current profile sync behavior.

## Later

- #19 Add Redis - current Caffeine usage appears limited to OAuth request token cache; defer until real scale/runtime need appears.
- #11 Parsing optimization - workout import already uses `saveAll`; verify before optimizing.
- #2 OpenCSV or Apache Commons CSV - parser replacement is a low-priority maintainability choice unless current parsing fails.

## Contradictions

- Several open GitHub issues describe work that appears present in current code. These cards should be verified and closed or rewritten rather than implemented blindly.

## Open Questions

- Should the GitHub Project board be physically updated after this triage page lands?
- Does issue #7 require Spring Modulith JDBC event publication/outbox semantics beyond current event listeners?
- Does issue #4 require historical backfill beyond current day/month sync methods?

## Next Actions

- Use [[nutrition-sync]] and [[daily-insight]] to scope the next real feature issue.
- Use [[ci-and-quality-gates]] to verify #18 after branch integration.
- Deduplicate #8 and #14 before adding more FatSecret profile work.
