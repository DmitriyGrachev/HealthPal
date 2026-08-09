# FitnessApp Codex Review Prompt

Review the current branch or uncommitted changes for FitnessApp.

Prioritize findings over summary. Report only actionable issues with file and line references when possible. Focus on:

- Security: Spring Security matchers, role gates, auth bypasses, JWT handling, brute force/rate limits, token storage, dependency CVEs.
- Privacy: raw Telegram message text, nutrition details, user notes, prompt bodies, OAuth tokens, JWTs, API keys, FatSecret tokens, or AI context leaking to logs.
- Tests: missing focused unit tests, MockMvc security tests, prompt rendering tests, and PostgreSQL/Testcontainers coverage for Flyway, pgvector, SQL, and JPA behavior.
- Architecture: Spring Modulith cycles, internal package imports across modules, events/exceptions in unstable packages, controller-to-persistence shortcuts.
- AI workflows: real provider calls in tests, brittle prompt string assertions, fallback behavior regressions, user-memory filtering by server-side user id.

Use `AGENTS.md` and `TESTING.md` as review rules. If `.graphify/graph.json` exists, use Graphify for broad impact analysis before reading large file sets.

Verification expectations:

- `mvn test` is the default gate.
- `mvn verify -Pintegration` is required for PostgreSQL/Flyway/pgvector integration behavior.
- `mvn test -Parchitecture` is required for Modulith boundary work.

Output format:

1. Findings first, ordered by severity.
2. Open questions or assumptions.
3. Brief test/verification gaps.
4. Short summary only after findings.
