---
type: topic
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../.github/workflows/ci.yml
  - ../../AGENTS.md
  - ../../TESTING.md
  - https://github.com/DmitriyGrachev/HealthPal/issues/18
tags:
  - fitnessapp
  - ci
  - quality-gates
---

# CI And Quality Gates

## What It Knows

The repository has a GitHub Actions workflow that runs unit/architecture tests, PostgreSQL integration tests, and dependency hygiene. This means old issue #18 appears implemented locally and likely belongs in Verify / Close after branch integration.

Local verification should still choose the gate that matches the change risk.

## Evidence

- `.github/workflows/ci.yml`
- `AGENTS.md`
- `TESTING.md`
- `pom.xml`
- https://github.com/DmitriyGrachev/HealthPal/issues/18
- [[testing-strategy]]
- [[github-project-triage]]

## Contradictions

- GitHub Project marks #18 as `In Progress`, while current repository evidence contains CI workflow jobs for the requested gates.

## Open Questions

- Should dependency hygiene remain `continue-on-error`, or become blocking after dependency cleanup stabilizes?
- Should the privacy scan run in CI?

## Next Actions

- Verify #18 after pushing/merging the branch that contains `.github/workflows/ci.yml`.
- Keep CI aligned with `mvn test`, `mvn test -Parchitecture`, and `mvn verify -Pintegration`.
- Treat dependency hygiene failures as signal even while the CI job is non-blocking.
