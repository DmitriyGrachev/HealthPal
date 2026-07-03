# FitnessApp Agent Guide

## Project Shape

FitnessApp is a Java 21 Maven/Spring Boot modular monolith. The main modules are `auth`, `nutrition`, `workout`, `analytics`, `ai`, `memory`, `telegram`, and `exception`.

Use the existing hexagonal style:

- `domain` for domain objects and records.
- `application/port/in` and `application/port/out` for use-case and boundary interfaces.
- `application/service` for use-case implementation.
- `adapter/in/web` for REST controllers and listeners.
- `adapter/out/persistence` for JPA/JDBC persistence adapters.
- `infrastructure` for framework configuration.

Keep changes surgical. Prefer existing package conventions, constructor injection, Java records for DTOs, and focused tests near the changed behavior.

## Commands

- Fast default gate: `mvn test`
- Future PostgreSQL/Testcontainers gate: `mvn verify -Pintegration`
- Spring Modulith gate: `mvn test -Parchitecture`
- Dependency tree examples:
  - `mvn dependency:tree "-Dincludes=org.springframework.ai"`
  - `mvn dependency:tree "-Dincludes=org.apache.tomcat.embed:tomcat-embed-core"`

The default Maven test gate excludes `*IntegrationTest.java` and `*ArchitectureTest.java`. Do not claim a task is done until the most relevant command has actually run, or clearly say why it could not be run.

## Testing Rules

Follow `TESTING.md` as the source of truth for test strategy.

- Prefer fast unit tests with JUnit 5, AssertJ, and Mockito.
- Use `@WebMvcTest` and MockMvc for controller, validation, and security rules where practical.
- Do not use H2 to prove PostgreSQL-specific Flyway, pgvector, SQL, or JPA behavior. Use PostgreSQL with Testcontainers for those checks.
- Mock AI providers in tests. Tests must not call real OpenRouter, Gemini, Telegram, or FatSecret services.
- Avoid brittle assertions on localized text. Prefer status, type, code, and stable fields.

## Architecture Rules

Use Spring Modulith boundaries deliberately. Avoid direct cycles between business modules; prefer public API packages, events in stable exposed packages, or ports when crossing modules.

Use `CONTEXT.md` for domain language and `docs/adr/` for recorded architecture decisions before proposing broad naming or module changes.

When working on architecture or broad codebase questions, use Graphify first:

- Start with `graphify summary --graph .graphify/graph.json`.
- Use `graphify query "<question>"`, `graphify path "<A>" "<B>"`, or `graphify explain "<concept>"` before reading broad file sets.
- For review impact on changed files, prefer `graphify review-delta --graph .graphify/graph.json` or `graphify review-analysis --graph .graphify/graph.json`.
- If `.graphify/needs_update` exists or `.graphify/branch.json` says stale, warn before relying on semantic results and update the graph when appropriate.
- After modifying code files, run `npx graphify hook-rebuild` when available.

Read `.graphify/GRAPH_REPORT.md` only for broad architecture review or when the query/path/explain commands are insufficient.

## Security And Privacy

- Treat `.env` and local secrets as private. Do not print, commit, or copy real secret values.
- Do not log raw Telegram message text, nutrition details, AI prompts containing user data, OAuth tokens, JWTs, API keys, or FatSecret tokens.
- Check `SecurityConfig` and existing MockMvc security tests when changing endpoints.
- For backlog security work, prefer the acceptance criteria in `BACKLOG.md`, but verify stale or garbled text against the actual code before editing.
- Existing Flyway migrations are immutable. Add a new migration for schema changes.

## AI And Prompt Work

AI provider behavior lives behind the `ai` module abstractions such as `AiModelPort`, `SmartAiRouter`, adapters, and orchestration services. Keep provider-specific exceptions and network behavior isolated from controllers and unrelated modules.

When changing prompts:

- Prefer versioned resource templates under `src/main/resources/ai/prompts/` for large or evolving prompts.
- Add rendering tests with fixture context.
- Do not call real providers during tests.
- Keep prompt changes separate from orchestration refactors when possible.

## Useful Repo Skills

Use these repo-local skills when the task matches:

- `$fitness-security-fix` for security backlog items, auth rules, privacy logging, dependency CVE upgrades, and secret handling.
- `$fitness-backlog-triage` for choosing, decoding, scoping, and preparing the next `BACKLOG.md` fix.
- `$fitness-testing` for adding or repairing tests and choosing the right Maven gate.
- `$spring-modulith-boundary` for module cycles, exposed APIs, events, and architecture profile failures.
- `$fitness-ai-prompts` for prompt extraction, prompt rendering tests, and AI provider workflow changes.

## Hooks And Review Prompts

This repository has a lightweight Codex Stop hook in `.codex/hooks/privacy-scan.ps1`. It scans changed text files for likely secrets and raw Telegram/privacy logging patterns. If the hook fires, inspect the named file and remove or mask the sensitive value instead of weakening the hook.

Use `.github/codex/prompts/review.md` as the stable review prompt for Codex PR or branch review workflows.

## Optional External Integrations

Sentry is not configured in this repository. Do not add Sentry MCP or Sentry SDK config unless a real Sentry organization/project/DSN is provided or committed configuration appears.

OpenAI Docs MCP and Graphify MCP are useful global Codex servers for this project. They should live in user/global Codex config rather than repo config because they include machine-local connection details.
