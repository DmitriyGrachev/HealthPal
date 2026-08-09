---
type: topic
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../TESTING.md
  - ../../AGENTS.md
  - ../../pom.xml
tags:
  - fitnessapp
  - testing
  - quality-gates
---

# Testing Strategy

## What It Knows

`TESTING.md` is the source of truth for test strategy. The default fast gate is `mvn test`. PostgreSQL-specific persistence behavior belongs behind `mvn verify -Pintegration`, and Spring Modulith boundary checks belong behind `mvn test -Parchitecture`.

Tests should prefer JUnit 5, AssertJ, Mockito, and MockMvc where practical. External providers should be mocked. H2 should not be used to prove PostgreSQL-specific Flyway, pgvector, SQL, or JPA behavior.

## Evidence

- `TESTING.md`
- `AGENTS.md`
- `pom.xml`
- `src/test/java/com/fit/fitnessapp/CodeHygieneTest.java`
- `src/test/java/com/fit/fitnessapp/module/ModuleArchitectureTest.java`
- `src/test/java/com/fit/fitnessapp/support/AbstractPostgresIntegrationTest.java`
- [[ci-and-quality-gates]]
- [[security-and-privacy]]

## Contradictions

- Some older test roadmap notes describe future work that is now partially implemented. Treat `TESTING.md` as strategy, but verify current code before opening new work.

## Open Questions

- Which remaining endpoints need MockMvc validation tests after the current validation work?
- Should docs-only changes always run `mvn test`, or is privacy scan plus Markdown validation enough after baseline is known green?

## Next Actions

- Choose the narrowest gate that proves the changed behavior.
- Run `mvn test` for broad Java behavior, `mvn verify -Pintegration` for PostgreSQL/Testcontainers behavior, and `mvn test -Parchitecture` for module boundary changes.
- Avoid brittle localized text assertions.
