# FitnessApp Roadmap and Issue Tracker

> Legacy note, 2026-07-05: this file is now a learning roadmap, not the active issue tracker. Use `BACKLOG.md`, `TESTING.md`, current code, and the Maven gates as the source of truth. Historical `SUPERSEDED` items may already be fixed or reshaped; re-verify before starting work from this file.
Last reviewed: 2026-07-05

This file is intentionally written as a learning roadmap, not only a bug list. The goal is to make the project stronger while understanding why each change matters.

## Current Snapshot

FitnessApp is a Java 21 / Spring Boot 3 backend for a fitness assistant. It includes auth, FatSecret nutrition sync, workouts, analytics, AI-generated insights, vector memory, Telegram bot commands, PostgreSQL, Flyway, and Spring Modulith.

The architecture direction is good: modular monolith + hexagonal architecture + domain events. The main problem is that the codebase only follows that architecture partially. The highest value work is to make tests pass, secure configuration, and make module boundaries real.

## How To Work With Codex On This Project

Use Codex as a senior pair-programmer and tutor. Do not ask only "fix this". Ask Codex to explain the code path, propose options, then implement one small step.

Good prompts:

- "Explain the request flow for /api/v1/nutrition/sync/today. Do not edit code yet."
- "Teach me why the Modulith test fails, then suggest the smallest fix."
- "Fix issue P0-03, but before editing show me the files you will touch and why."
- "After the fix, summarize what changed and quiz me with 3 questions."
- "Review my implementation of this issue. Prioritize bugs and architecture mistakes."

Suggested working loop:

1. Pick one issue from this file.
2. Ask Codex for a short explanation of the relevant code path.
3. Ask for a small implementation plan.
4. Let Codex edit only that issue.
5. Run the relevant tests.
6. Ask Codex to explain the diff.
7. Commit.

Avoid huge prompts like "fix all architecture". They waste context and make learning worse. Work in narrow vertical slices.

## IntelliJ IDEA Workflow

Recommended IntelliJ setup:

- Use Project SDK: Java 21.
- Import as Maven project.
- Enable annotation processing for Lombok.
- Set file encoding to UTF-8 globally and for the project.
- Use "Reformat Code" before commits.
- Run `mvn test` from IntelliJ or terminal before commits.
- Use the Maven tool window for lifecycle commands.
- Mark `src/main/java`, `src/main/resources`, `src/test/java`, and `src/test/resources` correctly if IntelliJ misses them.

Helpful plugins:

- Lombok
- SonarLint
- GitToolBox or GitLens-style equivalent
- Docker
- .ignore
- CheckStyle-IDEA, if Checkstyle is added
- PlantUML Integration, useful for Spring Modulith docs

## Priority Legend

- P0: Stop-the-line issue. Fix before adding features.
- P1: Important for correctness, security, or architecture.
- P2: Maintainability and developer experience.
- P3: Future improvement.

Status values:

- SUPERSEDED: historical item; verify against BACKLOG.md and current code before using
- IN_PROGRESS
- DONE
- DEFERRED

---

## Phase 0 - Safety and Project Hygiene

### P0-01: Rotate and remove committed Telegram bot token

Status: DONE

Location:

- `src/main/resources/application.properties`

Problem:

`telegram.bot.token` is committed in plain text. Treat it as leaked.

Fix:

- Done: token property now uses `telegram.bot.token=${TELEGRAM_BOT_TOKEN}`.
- Done: `.env` is ignored.
- Still recommended: if the old token was ever pushed to GitHub or shared, rotate it in BotFather and consider cleaning Git history.

Learning goal:

Understand why secrets in Git are compromised even after deletion.

### P0-02: Fix UTF-8 encoding corruption

Status: DONE

Location:

- Many Java files, `application.properties`, old issue docs

Problem:

Russian strings and comments are mojibake-corrupted. This affects Telegram replies, logs, AI prompts, and test expectations.

Fix:

- Done: obvious mojibake and question-mark corrupted comments/strings were cleaned from the scanned source/config files.
- Done: touched Java files compile as UTF-8 without BOM.
- Still recommended: set IntelliJ project encoding to UTF-8.
- Decided language policy:
  - code identifiers: English
  - internal comments/logs: English preferred
  - user-facing Russian: resource files or constants

Learning goal:

Understand source encoding, resource bundles, and user-facing text separation.

### P0-03: Remove or protect production test endpoints

Status: DONE

Location:

- `src/main/java/com/fit/fitnessapp/nutrition/adapter/in/web/TestController.java`

Problem:

`/test/sync-weight` and `/test/user-summary` are in main source. They can expose or mutate real user data.

Fix:

- Done: HTTP test controller is limited to `@Profile("dev")`.
- Done: `/test/**` is restricted to `ADMIN` in `SecurityConfig`.
- Done: Telegram `/test_generate` handler is limited to `@Profile("dev")`.

Learning goal:

Understand environment-specific beans and why debug endpoints are dangerous.

### P0-04: Audit and retire stale legacy tests

Status: IN_PROGRESS

Problem:

The current test suite is old and does not reliably describe the current application behavior. Forcing all old tests to pass may preserve outdated assumptions instead of improving confidence.

Fix:

- See `TESTING.md` for the detailed audit and step-by-step testing roadmap.
- Read each existing test and classify it as keep, rewrite, or delete.
- Keep tests that describe still-valid behavior.
- Rewrite tests that target valid behavior but use outdated setup or brittle assertions.
- Delete or disable tests that describe removed behavior, with a short explanation.
- Do not use `mvn test` as a release gate until this audit is complete.

Learning goal:

Understand that a test suite is a specification. If the specification is stale, first fix the specification.

---

## Phase 1 - Test Strategy, Security, and API Reliability

### P1-00: Build a step-by-step test coverage plan

Status: DONE

Problem:

The project needs useful test coverage, but adding random tests will create noise. The first goal is to decide what deserves tests and at what level.

Coverage map:

- Unit tests: pure business logic, validators, mappers, prompt builders, rate limiter behavior.
- Adapter tests: AI adapters, FatSecret adapter parsing/error handling, Telegram command handlers.
- Web tests: auth, nutrition, AI endpoints, validation errors, security access rules.
- Integration tests: Flyway + PostgreSQL persistence using Testcontainers.
- Architecture tests: Spring Modulith boundaries after module APIs are cleaned up.

Suggested order:

1. Start with fast unit tests for small services and helper logic.
2. Add controller tests for auth and nutrition endpoints.
3. Add adapter tests for AI error handling and FatSecret parsing.
4. Add Testcontainers PostgreSQL for Flyway/JPA/JDBC paths.
5. Re-enable architecture tests once module boundaries are intentionally fixed.

Definition of done:

- Done: `TESTING.md` exists.
- Existing tests are classified.
- New tests are added only for current behavior.
- CI eventually runs the stable subset.

Learning goal:

Understand how to choose the right test type for each risk.

### P1-01: Add global exception handling

Status: SUPERSEDED

Problem:

Controllers and services throw mixed exceptions. Some are caught locally; others may become generic 500 responses.

Fix:

- Add `@RestControllerAdvice`.
- Define a stable error response DTO.
- Map auth, validation, AI, external API, and not-found errors to proper HTTP statuses.

Learning goal:

Understand API error contracts.

### P1-02: Add request validation

Status: SUPERSEDED

Problem:

Request records/classes like login/register and query params have little validation.

Fix:

- Add `spring-boot-starter-validation` if needed.
- Use `@Valid`, `@NotBlank`, `@Email`, `@Size`, `@Positive`, `@PastOrPresent`.
- Add tests for invalid requests.

Learning goal:

Understand boundary validation vs domain validation.

### P1-03: Harden Spring Security configuration

Status: SUPERSEDED

Location:

- `SecurityConfig`
- `TokenFilter`
- `JwtCore`

Problems:

- Security TODO remains.
- CORS is disabled.
- Token lifetime is hardcoded.
- No password policy.
- No failed-login protection.
- Public endpoints and role rules need review.

Fix:

- Document every matcher in `SecurityConfig`.
- Configure CORS intentionally.
- Move JWT expiration to configuration.
- Add password validation.
- Add rate limiting for auth endpoints.

Learning goal:

Understand Spring Security filter chains and stateless JWT auth.

### P1-04: Stop logging sensitive AI/user context

Status: SUPERSEDED

Location:

- `MoeOrchestrator`
- AI services and adapters

Problem:

Full prompts may contain health data, notes, weight history, and personal context.

Fix:

- Remove full prompt logging.
- Log prompt IDs, task type, model, latency, and success/failure.
- Add debug-only redacted logging if needed.

Learning goal:

Understand privacy-aware logging.

---

## Phase 2 - Architecture and Modulith Boundaries

### P1-05: Fix Spring Modulith cycles

Status: SUPERSEDED

Observed cycle:

- `ai -> analytics -> auth -> telegram -> ai`
- `ai -> analytics -> nutrition -> auth -> telegram -> ai`

Problem:

The app claims modular architecture, but modules import non-exposed types and form cycles.

Fix approach:

- Add root `package-info.java` with `@ApplicationModule` where appropriate.
- Add `@NamedInterface` to exported API packages.
- Move cross-module events into exposed `api` packages.
- Do not import another module's internal `domain`, `application`, `adapter`, `repository`, or `entity` types.
- Make `ModuleTest.verifyArchitecture()` pass.

Learning goal:

Understand modular monolith boundaries.

### P1-06: Split `FitnessAiService`

Status: SUPERSEDED

Location:

- `FitnessAiService`

Problem:

This service handles Telegram ask events, nutrition sync events, daily insights, weekly reports, monthly reports, prompt construction, memory lookup, persistence, and event publishing.

Fix:

- Split into smaller services:
  - `DailyInsightService`
  - `ReportInsightService`
  - `TelegramAiQuestionService`
  - `PromptContextBuilder`
  - `InsightPersistenceService`
- Keep cross-module data access behind explicit ports or event payloads.

Learning goal:

Understand service responsibility boundaries.

### P1-07: Standardize module layout

Status: SUPERSEDED

Target layout:

- `domain`
- `application/service`
- `application/port/in`
- `application/port/out`
- `adapter/in`
- `adapter/out`
- `infrastructure`
- `api` for exported cross-module contracts

Problem:

Some modules follow this, others mix layers.

Fix:

- Do this gradually, one module at a time.
- Start with `ai` or `telegram`, because they create many cycles.

Learning goal:

Understand hexagonal architecture without over-engineering.

---

## Phase 3 - AI Reliability and Prompt Management

### P1-08: Fix `MoeOrchestrator` fallback logic

Status: SUPERSEDED

Location:

- `MoeOrchestrator`

Problem:

Weekly/monthly handling calls `smartAiRouter.callWithFallback(prompt)`, catches `AiUnavailableException`, then calls the same method again.

Fix:

- Define clear provider routing:
  - daily: selected configured model
  - quick ask: fast configured model
  - weekly/monthly: primary model with fallback
- Remove duplicate fallback.
- Add unit tests for routing behavior.

Learning goal:

Understand fallback design and testable routing.

### P1-09: Fix OpenRouter error handling

Status: SUPERSEDED

Location:

- `OpenRouterAdapter`

Problem:

All first-call exceptions are treated as parsing failures, causing a second call before classifying auth/400/timeout errors.

Fix:

- Separate provider call failures from output parsing failures.
- Do not retry auth/400 errors.
- Add tests for 401, 400, timeout, malformed JSON, and valid response.

Learning goal:

Understand exception classification and external adapter design.

### P2-01: Extract prompts from service methods

Status: SUPERSEDED

Problem:

Prompts are embedded in Java service methods, hard to version and test.

Fix:

- Create prompt template classes or resource files.
- Add prompt version metadata to saved insights.
- Add tests that verify required variables are present.

Learning goal:

Understand maintainable AI prompt engineering.

### P2-02: Add AI cost and usage controls

Status: SUPERSEDED

Fix:

- Track AI calls per user/day.
- Store model name, task type, latency, success/failure.
- Add budget/rate limits before paid models are used.

Learning goal:

Understand production AI operations.

---

## Phase 4 - Persistence, Data, and Integrations

### P1-10: Use PostgreSQL-compatible integration tests

Status: SUPERSEDED

Problem:

H2 fails on PostgreSQL-specific migrations such as `TIMESTAMPTZ`.

Fix:

- Prefer Testcontainers PostgreSQL for integration tests.
- Keep H2 only for pure unit/slice tests that do not run real migrations.

Learning goal:

Understand why test databases should match production behavior.

### P2-03: Review Flyway migration quality

Status: SUPERSEDED

Problems to inspect:

- Duplicate-ish versions like `V6`, `V6.1`, `V6.2`
- Typos in migration names
- Unused `conversation_history`
- PostgreSQL-specific types in tests

Fix:

- Keep existing migrations immutable if already applied.
- Add new corrective migrations.
- Document schema ownership by module.

Learning goal:

Understand migration discipline.

### P2-04: Make event handlers idempotent

Status: SUPERSEDED

Problem:

Some events can be redelivered and create duplicate memories or duplicate side effects.

Fix:

- Add unique keys or processed-event tracking.
- Make memory writes deterministic where possible.

Learning goal:

Understand event-driven reliability.

### P2-05: Improve FatSecret sync resilience

Status: SUPERSEDED

Problem:

Monthly sync loops over days and tolerates failures, but there is no clear retry/reporting model.

Fix:

- Add sync result object.
- Track partial failures.
- Add retry/backoff for transient API errors.
- Consider background jobs for long syncs.

Learning goal:

Understand external API reliability.

---

## Phase 5 - Developer Experience and Operations

### P1-11: Add CI pipeline

Status: SUPERSEDED

Fix:

- Add GitHub Actions workflow:
  - checkout
  - setup Java 21
  - cache Maven
  - run `mvn test`

Learning goal:

Understand automated quality gates.

### P2-06: Add README

Status: SUPERSEDED

Include:

- project purpose
- architecture overview
- prerequisites
- environment variables
- running locally
- running tests
- Docker/PostgreSQL setup
- common endpoints

Learning goal:

Understand project onboarding docs.

### P2-07: Add formatting and static analysis

Status: SUPERSEDED

Options:

- Spotless
- Checkstyle
- PMD
- Error Prone
- SonarLint locally

Fix:

- Start with Spotless or Checkstyle only.
- Avoid adding too many tools at once.

Learning goal:

Understand automated maintainability checks.

### P2-08: Add Actuator and basic observability

Status: SUPERSEDED

Fix:

- Add Spring Boot Actuator.
- Expose health endpoint.
- Add metrics for AI calls and sync jobs.

Learning goal:

Understand production visibility.

---

## Phase 6 - Product Roadmap

### Quick Wins

- Telegram `/status` command: linked account, last sync date, latest weight.
- Manual "generate today's insight" endpoint or command with clear status.
- API endpoint for insight history.
- Better error messages for missing FatSecret connection.
- Profile completeness endpoint.

### Medium Effort

- Weekly/monthly report history.
- User goals and progress tracking.
- Better workout import UX and validation.
- Nutrition anomaly detection without AI first, then AI explanation.
- Telegram onboarding flow.

### Long Term

- Frontend dashboard.
- AI coach memory review/edit screen.
- Background job system for sync/report generation.
- Multi-provider AI usage dashboard.
- Personalization based on goals, injuries, allergies, schedule, and adherence.

---

## Recommended Learning Order

1. Test strategy and legacy test audit.
2. Spring Security fundamentals.
3. Modulith boundaries.
4. Testcontainers and integration testing.
5. Error handling and API contracts.
6. Prompt management and AI reliability.
7. Observability and CI/CD.

## Next Best Issue To Start

Start with P0-04 and P1-00.

Reason:

- P0-01 and P0-02 are already done.
- P0-04 prevents old tests from misleading future work.
- P1-00 creates a realistic testing plan instead of chasing stale failures.

After that, continue with P1-01 and P1-03, then return to P1-05 when you are ready to fix module boundaries deliberately.
