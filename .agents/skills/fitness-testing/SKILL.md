---
name: fitness-testing
description: Chooses and implements the right FitnessApp test strategy for unit, MockMvc, PostgreSQL integration, AI, Telegram, nutrition, workout, and architecture changes. Use when adding tests, fixing failing tests, deciding Maven test gates, or stabilizing behavior described in TESTING.md.
---

# Fitness Testing

## Source Of Truth

Follow `TESTING.md` first. Prefer current intended behavior over stale tests.

## Choose The Test Type

- Use pure unit tests for services, routers, rate limiting, command handlers, prompt rendering, and DTO mapping.
- Use `@WebMvcTest` plus MockMvc for controllers, request validation, security status codes, and JSON response shape.
- Use PostgreSQL with Testcontainers for Flyway, pgvector, PostgreSQL SQL, JPA mappings, and real persistence behavior.
- Use `mvn test -Parchitecture` only for Spring Modulith boundary checks.

## Do Not

- Do not call real OpenRouter, Gemini, Telegram, or FatSecret services.
- Do not use H2 as proof for PostgreSQL-specific behavior.
- Do not assert localized message text unless the text itself is the contract.
- Do not keep mostly commented-out test files as coverage.

## Red-Green Loop

1. Read the production code and the current tests around the behavior.
2. Add the smallest failing test that describes the intended behavior.
3. Implement the minimal production change.
4. Run the narrow test.
5. Run `mvn test` when the change affects normal code paths.
6. Run `mvn verify -Pintegration` or `mvn test -Parchitecture` only when the changed surface requires it.

## Useful Commands

- All default tests: `mvn test`
- One test class: `mvn "-Dtest=ClassName" test`
- One test method: `mvn "-Dtest=ClassName#methodName" test`
- Integration profile: `mvn verify -Pintegration`
- Architecture profile: `mvn test -Parchitecture`

## Acceptance

End with the test command output summary, not just the command name. Mention any skipped broader gate and why.
