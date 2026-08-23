# FitnessApp Testing Strategy

Last verified: 2026-08-23

This document is the source of truth for test selection and release gates. The
Maven configuration in `pom.xml` and `.github/workflows/ci.yml` implement these
rules. Test counts are a verification snapshot, not a fixed acceptance
criterion; zero failures and the required behavior are what matter.

## Required Gates

| Gate | Command | What it runs |
|---|---|---|
| Fast default | `mvn test` | Surefire default test names (`*Test`, `*Tests`, `*TestCase`); excludes `*IntegrationTest` and `*ArchitectureTest` |
| PostgreSQL integration | `mvn verify -Pintegration` | The fast default suite, then Failsafe tests named `*IntegrationTest` against Testcontainers PostgreSQL |
| Architecture | `mvn test -Parchitecture` | Spring Modulith checks named `*ArchitectureTest`; the profile overrides the default exclusions |
| Dependency hygiene | `mvn dependency:analyze` | Fails on undeclared or unexplained dependency warnings |
| Privacy | `powershell -File .codex/hooks/privacy-scan.ps1` | Scans changed files for likely secrets and unsafe Telegram logging patterns without printing values |

For a Phase or release gate, run all five commands. For code changes, also run
`npx graphify hook-rebuild` and `git diff --check` as required by the execution
plan. A focused test command may shorten the RED/GREEN loop, but never replaces
the relevant final gate.

Verified Phase 0 snapshot on 2026-08-23:

- default: 362 tests, 0 failures/errors;
- integration: 362 default tests plus 114 PostgreSQL integration tests, 0 failures/errors;
- architecture: 4 tests, 0 failures/errors, 1 intentional documented skip;
- dependency analysis, privacy scan, and diff check: clean.

## Test Selection

### Unit and service tests

Use JUnit 5, AssertJ, and Mockito for deterministic domain behavior, state
transitions, validation, routing, parsing, budgets, retry classification, and
adapter mapping. Prefer constructor-created subjects and mocked ports. Do not
start Spring when framework behavior is not part of the question.

### Web and security tests

Use `@WebMvcTest` with explicit `@AutoConfigureMockMvc`. On Boot 4, replace
Spring context collaborators with `@MockitoBean` or `@MockitoSpyBean`.

Assert stable protocol behavior:

- HTTP status and content type;
- stable error code/type and required response fields;
- authentication, authorization, CORS, and validation rules;
- current-user scoping.

Avoid assertions on localized prose. Do not disable security filters unless the
test is explicitly about controller mapping/validation and security is covered
by a separate focused test.

### PostgreSQL integration tests

Use `*IntegrationTest` for behavior that depends on PostgreSQL, Flyway, JDBC,
JPA, pgvector, transactions, locks, concurrency, event publication, lifecycle
cleanup, or real Spring wiring. The shared base is
`AbstractPostgresIntegrationTest`, backed by `pgvector/pgvector:pg16`.

Rules:

- Never use H2 to prove PostgreSQL SQL, types, indexes, migrations, locking, or
  transaction behavior.
- Existing Flyway migrations are immutable. Add a forward migration and cover
  both clean-latest and relevant historical/dirty upgrade paths.
- Prefer deterministic latches, barriers, and database state observation over
  arbitrary sleeps in concurrency tests.
- Clean fixtures by owner and keep tests isolated across a shared container.
- Assert durable state and ownership, not incidental SQL formatting.

`HistoricalUpgradeIntegrationTest` is the primary proof for dirty historical
checkpoints and the latest schema. Migration-specific tests may use their own
container when they must control Flyway target versions.

### Architecture tests

`ModuleArchitectureTest` verifies Spring Modulith boundaries and exposed API
rules. Architecture documentation generation is intentionally separate from
the verification assertion. When changing cross-module dependencies, public
API packages, events, or ports:

1. inspect the impact with Graphify;
2. run focused tests for the affected producer and consumer;
3. run `mvn test -Parchitecture`;
4. run the default and integration gates when runtime wiring or persistence is
   affected.

Do not solve a boundary failure with a broad allow-list or a root-package
escape hatch. Expose the smallest stable API or use a port/event.

### AI, Telegram, FatSecret, and other external boundaries

Tests must not call real OpenRouter, Gemini, Telegram, FatSecret, or other
providers. Mock the provider port/client and verify:

- typed provider failure classification;
- deadline, attempt, concurrency, and token-budget behavior;
- transaction boundaries around external I/O;
- idempotency/fencing and terminal or uncertain recovery states;
- privacy-safe logs and persisted error codes;
- prompt rendering with fixture context, without real secrets or personal data.

### Serialization and compatibility

Application JSON uses Jackson 3 (`tools.jackson`). Third-party Jackson 2 is an
isolated compatibility boundary and must not leak into application imports.
Serialization tests should deserialize and compare typed values instead of
asserting incidental textual formatting such as a particular date encoding.

## Naming and Placement

- `*Test.java`: fast unit, service, adapter, MockMvc, and hygiene tests.
- `*IntegrationTest.java`: PostgreSQL/Testcontainers or full Spring integration
  behavior.
- `*ArchitectureTest.java`: Spring Modulith verification only.
- Keep tests near the module and package whose behavior they specify.
- One test should answer one observable question; use parameterized tests for
  a true behavior matrix rather than duplicated methods.

## Change-to-Gate Matrix

| Change | Minimum focused work | Required final gates |
|---|---|---|
| Pure domain/service branch | Affected unit tests | `mvn test` |
| Controller, validation, security | MockMvc tests | `mvn test` |
| SQL, JPA, Flyway, pgvector, locks | Relevant PostgreSQL tests | default + integration |
| Cross-module API/event/port | Producer/consumer tests | default + architecture; integration if persisted/asynchronous |
| AI/provider/prompt | Rendering/routing/provider-boundary tests | default; integration if durable state changes |
| Dependency/platform upgrade | Compile/configuration tests and dependency trees | all required gates + `dependency:analyze` |
| Privacy/lifecycle/export/delete | Focused lifecycle and log tests | all required gates + privacy scan |

When several rows apply, use the union of their gates.

## Failure Policy

1. Reproduce the smallest failing test.
2. Determine whether the implementation, fixture, environment, or documented
   contract is wrong.
3. Fix the root cause and add/strengthen the regression assertion.
4. Rerun the focused test, then every relevant final gate.

Do not delete, disable, loosen, or rename a valid test merely to make a gate
green. An unavailable Docker runtime is an environmental blocker for the
integration gate, not permission to substitute H2 or claim completion.

## CI Contract

GitHub Actions runs three jobs:

- unit and architecture: `mvn -B test`, then `mvn -B -Parchitecture test`;
- PostgreSQL integration: `mvn -B verify -Pintegration`;
- dependency/privacy hygiene: `mvn -B dependency:analyze` plus a
  privacy-sensitive logging pattern check.

Local Phase/release verification additionally uses the repository PowerShell
privacy hook because it checks changed and untracked files without exposing
matched values.
