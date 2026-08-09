---
name: fitness-backlog-triage
description: Guides FitnessApp BACKLOG.md triage by decoding garbled entries, validating affected files, selecting the next safe fix, mapping acceptance criteria to tests, and choosing the right Maven gate. Use when the user asks what backlog item to do next, mentions a FIX number, wants to break down BACKLOG.md, or needs to prepare a focused implementation plan from backlog text.
---

# Fitness Backlog Triage

## Workflow

1. Read the requested `BACKLOG.md` section or search by FIX id.
2. Treat garbled Cyrillic as lossy text. Reconstruct intent from affected files, acceptance criteria, code, and tests.
3. Verify the affected files exist and inspect the current implementation before proposing work.
4. Classify the item:
   - Security/privacy/auth
   - Dependency/infra
   - Architecture/Modulith
   - Data/Flyway/PostgreSQL
   - API/docs/polish
5. Identify the smallest independently shippable slice.
6. Map each acceptance criterion to a concrete test or command.
7. Recommend the next action and name the skill to use next.

## Priority Heuristics

- Prefer P0 security/privacy items before P1/P2 work.
- Prefer small P0 fixes that add durable tests before large architecture moves.
- Avoid starting dependency upgrades and broad refactors in the same slice.
- For PostgreSQL, Flyway, pgvector, or SQL behavior, plan Testcontainers coverage rather than H2.
- For Modulith cycles, use `$spring-modulith-boundary`.
- For auth matchers, privacy logs, CVEs, and secret handling, use `$fitness-security-fix`.

## Output Shape

Return:

- Chosen FIX id and one-sentence goal.
- Why this item is next.
- Files to inspect first.
- Test plan and Maven gate.
- Risks or stale assumptions.
- Recommended next command or skill invocation.

## Guardrails

- Do not edit code during triage unless the user explicitly asks to implement.
- Do not trust backlog claims without checking current code.
- Do not auto-stage, commit, or mutate `.graphify` state.
