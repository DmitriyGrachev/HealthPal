# LLM Wiki Vault Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a public-safe, Git-committed `docs/brain/` LLM wiki vault for FitnessApp that compiles repository docs, codebase structure, and GitHub Project state into durable Obsidian-style knowledge pages.

**Architecture:** The vault is Markdown-only and lives under `docs/brain/`. `schema.md` defines maintenance rules, `index.md` is the navigation catalog, `log.md` is append-only chronology, and category folders hold source summaries, module pages, topic synthesis, workflow descriptions, and GitHub issue triage.

**Tech Stack:** Markdown, YAML frontmatter, Obsidian-style `[[wikilinks]]`, GitHub CLI (`gh`) for Project board reads, existing repo privacy scan hook, PowerShell validation commands.

---

## File Structure

Create the vault:

```text
docs/brain/
  README.md
  schema.md
  index.md
  log.md
  sources/
    repo-docs.md
    github-project-2026-07-06.md
  modules/
    auth.md
    nutrition.md
    workout.md
    ai.md
    memory.md
    telegram.md
    analytics.md
  topics/
    testing-strategy.md
    security-and-privacy.md
    ci-and-quality-gates.md
    fatsecret-integration.md
    spring-modulith.md
  issues/
    github-project-triage.md
  workflows/
    nutrition-sync.md
    daily-insight.md
    telegram-ask.md
```

Responsibilities:

- `README.md`: human entry point for the vault.
- `schema.md`: agent operating manual for ingest, query, lint, privacy, evidence priority, and page format.
- `index.md`: all pages listed by category with one-line summaries and wikilinks.
- `log.md`: chronological record of the initial ingest and future wiki maintenance events.
- `sources/repo-docs.md`: source summary of committed repository docs and architecture constraints.
- `sources/github-project-2026-07-06.md`: source summary of GitHub Project board item state on 2026-07-06.
- `modules/*.md`: current module knowledge with evidence and open questions.
- `topics/*.md`: cross-cutting project practices and risks.
- `issues/github-project-triage.md`: interpreted next/verify/later grouping for stale GitHub Project work.
- `workflows/*.md`: durable workflow summaries for key product/system flows.

The design spec is `docs/superpowers/specs/2026-07-06-llm-wiki-vault-design.md`.

---

### Task 1: Create Vault Skeleton And Core Operating Files

**Files:**
- Create: `docs/brain/README.md`
- Create: `docs/brain/schema.md`
- Create: `docs/brain/index.md`
- Create: `docs/brain/log.md`
- Create directories: `docs/brain/sources/`, `docs/brain/modules/`, `docs/brain/topics/`, `docs/brain/issues/`, `docs/brain/workflows/`, `docs/brain/decisions/`

- [ ] **Step 1: Create the directory tree**

Run:

```powershell
New-Item -ItemType Directory -Force `
  docs\brain, `
  docs\brain\sources, `
  docs\brain\modules, `
  docs\brain\topics, `
  docs\brain\issues, `
  docs\brain\workflows, `
  docs\brain\decisions | Out-Null
```

Expected: exit code 0 and all directories exist.

- [ ] **Step 2: Write `README.md`**

Create `docs/brain/README.md` with this content:

```markdown
---
type: index
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../superpowers/specs/2026-07-06-llm-wiki-vault-design.md
tags:
  - fitnessapp
  - llm-wiki
---

# FitnessApp Brain

This vault is the public-safe LLM-maintained knowledge layer for FitnessApp. It compiles repository docs, current code evidence, GitHub Project state, and recurring agent findings into linked Markdown pages.

The vault follows [[schema]] and starts from [[repo-docs]] plus [[github-project-2026-07-06]]. Use [[index]] as the navigation catalog and [[log]] as the chronological maintenance history.

## What Belongs Here

- Architecture and module summaries grounded in committed source files.
- Cross-cutting topics such as testing, privacy, CI, FatSecret integration, and Spring Modulith.
- Workflow summaries for nutrition sync, daily insights, Telegram commands, and similar project behavior.
- GitHub Project triage that interprets old issues against current code evidence.

## What Does Not Belong Here

- Real secrets, tokens, local `.env` values, JWTs, OAuth credentials, or API keys.
- Raw Telegram message text, private health notes, raw nutrition logs, FatSecret raw payloads, or AI prompts containing user data.
- A mirror of complete GitHub issue bodies.

## Maintenance Loop

1. Ingest new sources into `sources/`.
2. Update affected module, topic, workflow, issue, or decision pages.
3. Update [[index]].
4. Append an entry to [[log]].
5. Run the privacy scan before committing.
```

- [ ] **Step 3: Write `schema.md`**

Create `docs/brain/schema.md` with this content:

```markdown
---
type: schema
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../superpowers/specs/2026-07-06-llm-wiki-vault-design.md
  - ../../AGENTS.md
tags:
  - fitnessapp
  - llm-wiki
  - agent-rules
---

# Brain Schema

This file is the operating manual for maintaining `docs/brain/`.

## Core Rule

The vault is a compiled knowledge layer. It does not replace source files, ADRs, tests, GitHub issues, or the application code. It summarizes and connects those sources so future agents can start from accumulated understanding.

## Page Frontmatter

Every page must start with YAML frontmatter:

```yaml
---
type: module | topic | source | issue-triage | decision | workflow | index | log | schema
status: current | stale | draft | needs-review
owner: codex
updated: 2026-07-06
sources:
  - ../../AGENTS.md
tags:
  - fitnessapp
---
```

## Synthetic Page Sections

Use these sections for module, topic, workflow, decision, and issue-triage pages:

- `What It Knows`
- `Evidence`
- `Contradictions`
- `Open Questions`
- `Next Actions`

Source pages may use `Source Scope`, `Extracted Facts`, `Useful Links`, and `Impact On Wiki`.

## Ingest Workflow

1. Read the source and identify whether it is raw evidence, already-synthesized documentation, or current code.
2. Write or update a summary in `sources/`.
3. Update affected pages in `modules/`, `topics/`, `workflows/`, `issues/`, or `decisions/`.
4. Update [[index]].
5. Append a dated entry to [[log]].
6. Run the privacy scan before committing.

## Query Workflow

1. Read [[index]] first.
2. Read relevant vault pages.
3. Use raw source files, Graphify, or GitHub only when the vault is missing evidence or might be stale.
4. If the answer produces durable project knowledge, update the relevant page or ask whether to save it.

## Lint Workflow

Check for stale claims, broken wikilinks, pages missing from [[index]], orphan pages, unclear evidence, GitHub issues that appear complete in code, and privacy-sensitive details.

## Evidence Priority

When sources disagree, prefer:

1. current code and tests;
2. committed ADRs and stable docs;
3. current GitHub Project metadata;
4. GitHub issue bodies;
5. old backlog text;
6. conversation memory.

Record disagreements under `Contradictions`.

## Privacy Rules

Do not store real secrets, tokens, local `.env` values, JWTs, OAuth credentials, API keys, raw Telegram text, raw nutrition logs, private health notes, raw FatSecret payloads, or AI prompts containing user data. Summarize sensitive workflows structurally.
```

- [ ] **Step 4: Write initial `index.md`**

Create `docs/brain/index.md` with this content:

```markdown
---
type: index
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../superpowers/specs/2026-07-06-llm-wiki-vault-design.md
tags:
  - fitnessapp
  - llm-wiki
---

# Brain Index

## Core

- [[README]] - Human entry point for the FitnessApp brain.
- [[schema]] - Agent maintenance rules for the vault.
- [[log]] - Chronological maintenance history.

## Sources

- [[repo-docs]] - Source summary for committed repository docs and architecture constraints.
- [[github-project-2026-07-06]] - Source summary for GitHub Project board state on 2026-07-06.

## Modules

- [[auth]] - Auth, user identity, notes, security boundaries, and current-user resolution.
- [[nutrition]] - Nutrition data, FatSecret sync, profiles, weight history, and persistence.
- [[workout]] - Workout import, parsing, persistence, and analytics queries.
- [[ai]] - AI orchestration, prompt templates, model routing, and insights.
- [[memory]] - User memory, pgvector, cleanup, and retrieval boundaries.
- [[telegram]] - Telegram command handling, linking, privacy-safe logs, and event entry points.
- [[analytics]] - Weekly and monthly report orchestration.

## Topics

- [[testing-strategy]] - Maven gates, unit/web/integration/architecture tests, and Testcontainers strategy.
- [[security-and-privacy]] - Secrets, logging, auth gates, and privacy-sensitive flows.
- [[ci-and-quality-gates]] - GitHub Actions, Maven profiles, dependency hygiene, and verification commands.
- [[fatsecret-integration]] - FatSecret OAuth, token storage, sync, profile, weight, and exercise APIs.
- [[spring-modulith]] - Module boundaries, events, architecture test, and cross-module integration rules.

## Issues

- [[github-project-triage]] - Current interpretation of old GitHub Project items against repository evidence.

## Workflows

- [[nutrition-sync]] - Manual and scheduled nutrition synchronization.
- [[daily-insight]] - Daily AI insight generation and delivery path.
- [[telegram-ask]] - Telegram ask flow, rate limiting, events, and AI handling.
```

- [ ] **Step 5: Write initial `log.md`**

Create `docs/brain/log.md` with this content:

```markdown
---
type: log
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../superpowers/specs/2026-07-06-llm-wiki-vault-design.md
tags:
  - fitnessapp
  - llm-wiki
---

# Brain Log

## [2026-07-06] ingest | Initial FitnessApp Brain

Initialized the public-safe LLM wiki vault structure from the approved design. The first ingest covers repository docs, current code structure, and GitHub Project board state, with privacy rules from `AGENTS.md` and the design spec.
```

- [ ] **Step 6: Validate skeleton files**

Run:

```powershell
$expected = @(
  'docs/brain/README.md',
  'docs/brain/schema.md',
  'docs/brain/index.md',
  'docs/brain/log.md'
)
$missing = $expected | Where-Object { -not (Test-Path -LiteralPath $_) }
if ($missing) { $missing; exit 1 }
'core brain files present'
```

Expected: prints `core brain files present`.

- [ ] **Step 7: Commit skeleton**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .codex\hooks\privacy-scan.ps1
git add docs/brain/README.md docs/brain/schema.md docs/brain/index.md docs/brain/log.md
git commit -m "docs: initialize llm wiki vault skeleton"
```

Expected: privacy scan exit code 0 and one commit containing only the four core vault files.

---

### Task 2: Ingest Repository Docs And GitHub Project Sources

**Files:**
- Create: `docs/brain/sources/repo-docs.md`
- Create: `docs/brain/sources/github-project-2026-07-06.md`
- Modify: `docs/brain/index.md`
- Modify: `docs/brain/log.md`

- [ ] **Step 1: Refresh GitHub Project data**

Run:

```powershell
gh project item-list 1 --owner DmitriyGrachev --format json --limit 100
```

Expected: JSON with `totalCount` at least `26`, including issues 1, 2, 4, 7, 8, 11, 13, 14, 18, 19, 23, and 29.

- [ ] **Step 2: Write `repo-docs.md`**

Create `docs/brain/sources/repo-docs.md` with these sections and facts:

```markdown
---
type: source
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../AGENTS.md
  - ../../README.md
  - ../../CONTEXT.md
  - ../../TESTING.md
  - ../../BACKLOG.md
  - ../../docs/adr/
tags:
  - fitnessapp
  - source
  - repo-docs
---

# Repository Docs Source Summary

## Source Scope

This page summarizes committed repository documentation used to initialize the vault.

## Extracted Facts

- FitnessApp is a Java 21 Maven/Spring Boot modular monolith.
- Main modules include `auth`, `nutrition`, `workout`, `analytics`, `ai`, `memory`, `telegram`, and `exception`.
- The project follows a hexagonal style with `domain`, `application/port/in`, `application/port/out`, `application/service`, `adapter/in/web`, `adapter/out/persistence`, and `infrastructure`.
- `mvn test` is the fast default gate.
- `mvn verify -Pintegration` is the PostgreSQL/Testcontainers gate.
- `mvn test -Parchitecture` is the Spring Modulith gate.
- `TESTING.md` is the source of truth for test strategy.
- Broad architecture work should use Graphify first.
- Security rules forbid logging raw Telegram text, nutrition details, user notes, AI prompts containing user data, OAuth tokens, JWTs, API keys, and FatSecret tokens.
- Existing Flyway migrations are immutable; schema changes require new migrations.
- AI provider behavior belongs behind `ai` module abstractions and must not call real providers in tests.

## Impact On Wiki

- Module pages should follow the module names and hexagonal boundaries from `AGENTS.md`.
- Testing, security/privacy, and CI topics should cite `TESTING.md`, `.github/workflows/ci.yml`, and `BACKLOG.md`.
- Workflow pages should avoid private examples and describe flows structurally.

## Useful Links

- [[testing-strategy]]
- [[security-and-privacy]]
- [[ci-and-quality-gates]]
- [[spring-modulith]]
```

- [ ] **Step 3: Write `github-project-2026-07-06.md`**

Create `docs/brain/sources/github-project-2026-07-06.md` with these sections and facts:

```markdown
---
type: source
status: current
owner: codex
updated: 2026-07-06
sources:
  - https://github.com/users/DmitriyGrachev/projects/1
  - https://github.com/DmitriyGrachev/HealthPal/issues
tags:
  - fitnessapp
  - source
  - github-project
---

# GitHub Project Source Summary - 2026-07-06

## Source Scope

This page summarizes GitHub Project board 1 for `DmitriyGrachev/HealthPal` as reviewed on 2026-07-06 with GitHub CLI.

## Extracted Facts

- Project title: `@DmitriyGrachev's HealthPal board`.
- Project visibility: public.
- Project item count: 26.
- Open issues visible from GitHub include #1, #2, #4, #7, #8, #11, #13, #14, #18, #19, #23, and #29.
- Items marked `In Progress` included #18, #23, and #29.
- Items marked `Todo` included #2, #4, #8, #11, #13, #14, and #19.
- Several Done cards remain useful as historical evidence, including #3, #5, #6, #9, #10, #12, #15, #16, #17, #20, #21, #22, #24, #26, #27, and #28.

## Project Hygiene Observations

- The board is stale because multiple open cards appear partially or fully implemented in current code.
- CI/CD appears implemented locally through `.github/workflows/ci.yml`, so #18 needs verification and likely closure after branch integration.
- FatSecret controller/service/repository and profile behavior exist in code, so #1, #8, and #14 need dedupe or verification.
- Spring Modulith dependencies, listeners, and architecture tests exist, so #7 needs verification against the exact transactional outbox expectation.
- Nutrition sync and daily insight scheduling remain likely active product workflow areas.

## Impact On Wiki

- [[github-project-triage]] should interpret old cards against code evidence instead of trusting Project status alone.
- [[ci-and-quality-gates]], [[fatsecret-integration]], [[spring-modulith]], [[nutrition-sync]], and [[daily-insight]] should link to the relevant issue evidence.
```

- [ ] **Step 4: Append source ingest to `log.md`**

Append:

```markdown

## [2026-07-06] ingest | Repository Docs And GitHub Project

Added source summaries for committed repository documentation and GitHub Project board state. The board is treated as stale input that must be checked against code and tests before implementation decisions.
```

- [ ] **Step 5: Validate source pages are indexed**

Run:

```powershell
rg -n "\[\[repo-docs\]\]|\[\[github-project-2026-07-06\]\]" docs/brain/index.md
rg -n "GitHub Project Source Summary|Repository Docs Source Summary" docs/brain/sources
```

Expected: both commands print matches.

- [ ] **Step 6: Commit sources**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .codex\hooks\privacy-scan.ps1
git add docs/brain/index.md docs/brain/log.md docs/brain/sources/repo-docs.md docs/brain/sources/github-project-2026-07-06.md
git commit -m "docs: ingest llm wiki source summaries"
```

Expected: privacy scan exit code 0 and one commit containing the two source pages plus index/log updates.

---

### Task 3: Add Module Pages

**Files:**
- Create: `docs/brain/modules/auth.md`
- Create: `docs/brain/modules/nutrition.md`
- Create: `docs/brain/modules/workout.md`
- Create: `docs/brain/modules/ai.md`
- Create: `docs/brain/modules/memory.md`
- Create: `docs/brain/modules/telegram.md`
- Create: `docs/brain/modules/analytics.md`
- Modify: `docs/brain/log.md`

- [ ] **Step 1: Create `auth.md`**

Use frontmatter type `module`, status `current`, sources `../../AGENTS.md`, `../../CONTEXT.md`, `../../src/main/java/com/fit/fitnessapp/auth/`, and `../../src/test/java/com/fit/fitnessapp/auth/`.

Content facts:

- Auth owns user identity, login/registration, current-user resolution, user notes, JWT infrastructure, and security configuration.
- Controllers for personal workflows should resolve the current user instead of trusting arbitrary user IDs.
- Security tests cover protected endpoints and auth validation.
- Cross-module interactions should use public APIs or events rather than internal package imports.
- Open question: whether user and auth should remain combined or be split further is historical board work marked Done and should not be changed without a new architecture decision.

- [ ] **Step 2: Create `nutrition.md`**

Use sources `../../src/main/java/com/fit/fitnessapp/nutrition/`, `../../src/test/java/com/fit/fitnessapp/nutrition/`, `../../CONTEXT.md`, and GitHub issues #1, #4, #8, #14, #23.

Content facts:

- Nutrition owns FatSecret connection, token persistence, day/month sync, profile/weight history behavior, and nutrition query endpoints.
- `NutritionService` syncs day and month data through FatSecret token lookup and persistence ports.
- `NutritionPersistenceAdapter` performs idempotent day persistence by updating aggregates and synchronizing entries.
- `FatSecretProfileService` handles latest weight, weight history, exercise entries, and update weight behavior.
- `NutritionSyncScheduler` and `FatSecretProfileSyncService` are scheduled entry points.
- Contradiction: older GitHub issues still ask for FatSecret controller/service/repository/profile work, but current code already contains many of those pieces.
- Next action: verify nutrition initial-load/tail-sync behavior and daily insight handoff before implementing more FatSecret profile work.

- [ ] **Step 3: Create `workout.md`**

Use sources `../../src/main/java/com/fit/fitnessapp/workout/`, `../../src/test/java/com/fit/fitnessapp/workout/`, `../../src/test/java/com/fit/fitnessapp/jdbc/workout/`, and GitHub issues #2, #9, #10, #11.

Content facts:

- Workout owns Jefit import, parser warnings, persistence, and read-side analytics queries.
- `WorkoutImportService` imports parsed sessions and persists them with `saveAll`.
- Workout JDBC query tests protect SQL alias behavior.
- Older parsing optimization and CSV parser issues remain candidates for later verification rather than immediate rewrite.
- Open question: whether replacing custom CSV parsing with OpenCSV or Apache Commons CSV still provides enough value.

- [ ] **Step 4: Create `ai.md`**

Use sources `../../src/main/java/com/fit/fitnessapp/ai/`, `../../src/main/resources/ai/prompts/`, `../../src/test/java/com/fit/fitnessapp/ai/`, and GitHub issues #16, #23, #27.

Content facts:

- AI owns insight generation, prompt rendering, model routing, rate limiting, and event listeners for reports/questions.
- Large prompts live under `src/main/resources/ai/prompts/`.
- Tests must mock providers and avoid real OpenRouter/Gemini calls.
- Daily insight flow is related to issue #23 and the `daily-insight-v1.md` prompt.
- Next action: verify end-of-day daily insight scheduling and nutrition sync dependency.

- [ ] **Step 5: Create `memory.md`**

Use sources `../../src/main/java/com/fit/fitnessapp/memory/`, `../../src/test/java/com/fit/fitnessapp/memory/`, `../../BACKLOG.md`, and `../../TESTING.md`.

Content facts:

- Memory owns user memory storage, pgvector integration, retrieval, expiration metadata, and cleanup scheduling.
- Memory work needs PostgreSQL/Testcontainers coverage for pgvector-specific behavior.
- Privacy rules apply because memory can contain user context.
- Current backlog items around pgvector and cleanup were resolved in the local backlog, but future changes still need integration tests.

- [ ] **Step 6: Create `telegram.md`**

Use sources `../../src/main/java/com/fit/fitnessapp/telegram/`, `../../src/test/java/com/fit/fitnessapp/telegram/`, `../../AGENTS.md`, and `../../BACKLOG.md`.

Content facts:

- Telegram owns bot update handling, command handlers, user linking, rate-limited ask flow, and event publication into other modules.
- Privacy rule: do not log raw Telegram message text.
- Link codes and `/ask` rate limiting have been security hardening areas.
- Telegram should publish events or use public APIs instead of direct persistence shortcuts across module boundaries.

- [ ] **Step 7: Create `analytics.md`**

Use sources `../../src/main/java/com/fit/fitnessapp/analytics/`, `../../src/test/java/com/fit/fitnessapp/analytics/`, and `../../CONTEXT.md`.

Content facts:

- Analytics owns weekly and monthly report orchestration.
- Weekly and monthly orchestrators are scheduled operational batch workflows.
- Security rules distinguish all-user batch report endpoints from personal workflows.
- Analytics publishes events that AI listens to for report generation.

- [ ] **Step 8: Append module ingest to `log.md`**

Append:

```markdown

## [2026-07-06] ingest | Module Pages

Added first-pass module pages for auth, nutrition, workout, AI, memory, telegram, and analytics. Pages emphasize current code evidence, privacy constraints, module boundaries, and stale GitHub issue contradictions.
```

- [ ] **Step 9: Validate module pages**

Run:

```powershell
$modules = 'auth','nutrition','workout','ai','memory','telegram','analytics'
foreach ($module in $modules) {
  $path = "docs/brain/modules/$module.md"
  if (-not (Test-Path -LiteralPath $path)) { throw "Missing $path" }
  rg -n "## What It Knows|## Evidence|## Open Questions|## Next Actions" $path
}
'module pages present'
```

Expected: headings print for each module and final line prints `module pages present`.

- [ ] **Step 10: Commit module pages**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .codex\hooks\privacy-scan.ps1
git add docs/brain/modules docs/brain/log.md
git commit -m "docs: add llm wiki module pages"
```

Expected: privacy scan exit code 0 and one commit containing module pages plus log update.

---

### Task 4: Add Cross-Cutting Topic Pages

**Files:**
- Create: `docs/brain/topics/testing-strategy.md`
- Create: `docs/brain/topics/security-and-privacy.md`
- Create: `docs/brain/topics/ci-and-quality-gates.md`
- Create: `docs/brain/topics/fatsecret-integration.md`
- Create: `docs/brain/topics/spring-modulith.md`
- Modify: `docs/brain/log.md`

- [ ] **Step 1: Create `testing-strategy.md`**

Facts:

- Fast default gate is `mvn test`.
- PostgreSQL/Testcontainers gate is `mvn verify -Pintegration`.
- Spring Modulith gate is `mvn test -Parchitecture`.
- Tests should prefer JUnit 5, AssertJ, Mockito, and MockMvc where practical.
- Do not use H2 to prove PostgreSQL-specific Flyway, pgvector, SQL, or JPA behavior.
- AI, Telegram, FatSecret, and external providers must be mocked.
- Stable assertions should prefer status, type, code, and stable fields over localized text.

- [ ] **Step 2: Create `security-and-privacy.md`**

Facts:

- Treat `.env` and local secrets as private.
- Do not log raw Telegram text, nutrition details, user notes, AI prompts with user data, OAuth tokens, JWTs, API keys, or FatSecret tokens.
- SecurityConfig and MockMvc security tests are required context when changing endpoints.
- Existing Flyway migrations are immutable.
- The repo privacy hook scans changed files for likely secrets and raw logging patterns.

- [ ] **Step 3: Create `ci-and-quality-gates.md`**

Facts:

- `.github/workflows/ci.yml` exists and runs unit/architecture tests, PostgreSQL integration tests, and dependency hygiene.
- CI issue #18 likely belongs in verify/close after branch integration because workflow evidence exists.
- Dependency hygiene is currently allowed to continue on error.
- Local gate selection should match the change risk.

- [ ] **Step 4: Create `fatsecret-integration.md`**

Facts:

- FatSecret behavior lives behind nutrition abstractions such as `FatSecretApiPort`.
- OAuth tokens are stored through nutrition persistence and must remain encrypted/private.
- FatSecret adapter must not log raw response bodies containing private nutrition/profile data.
- Profile behavior includes latest weight, weight history, update weight, exercises, and exercise entries.
- Old issues #1, #8, and #14 overlap and should be deduped or verified against current code.

- [ ] **Step 5: Create `spring-modulith.md`**

Facts:

- The project uses Spring Modulith dependencies and `ModuleArchitectureTest`.
- Module boundaries should avoid internal package imports across business modules.
- Cross-module communication should use public APIs, events in stable packages, or ports.
- Events/listeners exist across AI, memory, nutrition, telegram, auth, and analytics flows.
- Issue #7 needs verification specifically around transactional outbox expectations.

- [ ] **Step 6: Append topic ingest to `log.md`**

Append:

```markdown

## [2026-07-06] ingest | Cross-Cutting Topics

Added topic pages for testing strategy, security/privacy, CI gates, FatSecret integration, and Spring Modulith boundaries.
```

- [ ] **Step 7: Validate topic pages**

Run:

```powershell
$topics = 'testing-strategy','security-and-privacy','ci-and-quality-gates','fatsecret-integration','spring-modulith'
foreach ($topic in $topics) {
  $path = "docs/brain/topics/$topic.md"
  if (-not (Test-Path -LiteralPath $path)) { throw "Missing $path" }
  rg -n "## What It Knows|## Evidence|## Open Questions|## Next Actions" $path
}
'topic pages present'
```

Expected: headings print for each topic and final line prints `topic pages present`.

- [ ] **Step 8: Commit topic pages**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .codex\hooks\privacy-scan.ps1
git add docs/brain/topics docs/brain/log.md
git commit -m "docs: add llm wiki topic pages"
```

Expected: privacy scan exit code 0 and one commit containing topic pages plus log update.

---

### Task 5: Add Workflow Pages

**Files:**
- Create: `docs/brain/workflows/nutrition-sync.md`
- Create: `docs/brain/workflows/daily-insight.md`
- Create: `docs/brain/workflows/telegram-ask.md`
- Modify: `docs/brain/log.md`

- [ ] **Step 1: Create `nutrition-sync.md`**

Facts:

- Manual endpoints include `/api/v1/nutrition/sync/today` and `/api/v1/nutrition/sync/current-month`.
- `NutritionService` resolves the user's FatSecret token before syncing.
- Missing FatSecret connection maps to a clear external connection error.
- `NutritionPersistenceAdapter` keeps day persistence idempotent by updating aggregates and synchronizing entries.
- `NutritionSyncScheduler` is a scheduled entry point.
- Issue #4 asks for initial load plus scheduled tail upsert; current code supports sync pieces but initial-load/tail semantics need verification.

- [ ] **Step 2: Create `daily-insight.md`**

Facts:

- Daily insight behavior lives in the AI module and uses structured prompt resources.
- `daily-insight-v1.md` is the prompt template evidence.
- `/api/v1/ai/insights/today` can return pending when the daily insight is missing.
- `/api/v1/ai/insights/generate` accepts daily insight generation.
- Issue #23 connects daily insight with scheduled nutrition sync.
- Open question: whether end-of-day scheduling should first sync nutrition, then generate insight, then notify Telegram.

- [ ] **Step 3: Create `telegram-ask.md`**

Facts:

- Telegram ask is rate-limited before publishing an AI request event.
- Handler tests should avoid real Telegram or AI providers.
- Privacy logs must not include raw message text.
- AI listens for Telegram ask events and Telegram sends response notifications through event/listener flow.

- [ ] **Step 4: Append workflow ingest to `log.md`**

Append:

```markdown

## [2026-07-06] ingest | Workflow Pages

Added workflow pages for nutrition sync, daily insight, and Telegram ask. These pages connect user-facing behavior to modules, topics, and stale GitHub issues.
```

- [ ] **Step 5: Validate workflow pages**

Run:

```powershell
$workflows = 'nutrition-sync','daily-insight','telegram-ask'
foreach ($workflow in $workflows) {
  $path = "docs/brain/workflows/$workflow.md"
  if (-not (Test-Path -LiteralPath $path)) { throw "Missing $path" }
  rg -n "## What It Knows|## Evidence|## Open Questions|## Next Actions" $path
}
'workflow pages present'
```

Expected: headings print for each workflow and final line prints `workflow pages present`.

- [ ] **Step 6: Commit workflow pages**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .codex\hooks\privacy-scan.ps1
git add docs/brain/workflows docs/brain/log.md
git commit -m "docs: add llm wiki workflow pages"
```

Expected: privacy scan exit code 0 and one commit containing workflow pages plus log update.

---

### Task 6: Add GitHub Project Triage Page

**Files:**
- Create: `docs/brain/issues/github-project-triage.md`
- Modify: `docs/brain/log.md`

- [ ] **Step 1: Create `github-project-triage.md`**

Create a triage page with these groupings:

```markdown
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
```

- [ ] **Step 2: Append issue triage ingest to `log.md`**

Append:

```markdown

## [2026-07-06] ingest | GitHub Project Triage

Added GitHub Project triage that separates active work from stale verify/close cards and low-priority later items.
```

- [ ] **Step 3: Validate issue triage**

Run:

```powershell
rg -n "#29|#4|#13|#23|#18|#7|#1|#8|#14|#19|#11|#2" docs/brain/issues/github-project-triage.md
rg -n "\[\[nutrition-sync\]\]|\[\[daily-insight\]\]|\[\[ci-and-quality-gates\]\]" docs/brain/issues/github-project-triage.md
```

Expected: both commands print matches.

- [ ] **Step 4: Commit issue triage**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .codex\hooks\privacy-scan.ps1
git add docs/brain/issues/github-project-triage.md docs/brain/log.md
git commit -m "docs: add github project triage to brain"
```

Expected: privacy scan exit code 0 and one commit containing issue triage plus log update.

---

### Task 7: Final Vault Validation And Index Consistency

**Files:**
- Modify: `docs/brain/index.md`
- Modify: `docs/brain/log.md`

- [ ] **Step 1: Verify every Markdown page has frontmatter**

Run:

```powershell
$files = Get-ChildItem docs\brain -Recurse -Filter *.md
foreach ($file in $files) {
  $text = Get-Content -LiteralPath $file.FullName -Raw
  if (-not ($text.StartsWith("---`n") -or $text.StartsWith("---`r`n"))) {
    throw "Missing frontmatter: $($file.FullName)"
  }
}
"frontmatter ok: $($files.Count) files"
```

Expected: prints `frontmatter ok: 22 files` if analytics is included.

- [ ] **Step 2: Verify expected page count and file list**

Run:

```powershell
$expected = @(
  'docs/brain/README.md',
  'docs/brain/schema.md',
  'docs/brain/index.md',
  'docs/brain/log.md',
  'docs/brain/sources/repo-docs.md',
  'docs/brain/sources/github-project-2026-07-06.md',
  'docs/brain/modules/auth.md',
  'docs/brain/modules/nutrition.md',
  'docs/brain/modules/workout.md',
  'docs/brain/modules/ai.md',
  'docs/brain/modules/memory.md',
  'docs/brain/modules/telegram.md',
  'docs/brain/modules/analytics.md',
  'docs/brain/topics/testing-strategy.md',
  'docs/brain/topics/security-and-privacy.md',
  'docs/brain/topics/ci-and-quality-gates.md',
  'docs/brain/topics/fatsecret-integration.md',
  'docs/brain/topics/spring-modulith.md',
  'docs/brain/issues/github-project-triage.md',
  'docs/brain/workflows/nutrition-sync.md',
  'docs/brain/workflows/daily-insight.md',
  'docs/brain/workflows/telegram-ask.md'
)
$missing = $expected | Where-Object { -not (Test-Path -LiteralPath $_) }
if ($missing) { $missing; exit 1 }
"expected brain files present: $($expected.Count)"
```

Expected: prints `expected brain files present: 22`.

- [ ] **Step 3: Verify index links every created page**

Run:

```powershell
$slugs = @(
  'README','schema','log',
  'repo-docs','github-project-2026-07-06',
  'auth','nutrition','workout','ai','memory','telegram','analytics',
  'testing-strategy','security-and-privacy','ci-and-quality-gates','fatsecret-integration','spring-modulith',
  'github-project-triage',
  'nutrition-sync','daily-insight','telegram-ask'
)
$index = Get-Content docs\brain\index.md -Raw
$missing = $slugs | Where-Object { $index -notmatch [regex]::Escape("[[$_]]") }
if ($missing) { $missing; exit 1 }
"index links ok"
```

Expected: prints `index links ok`.

- [ ] **Step 4: Verify no placeholder language remains**

Run:

```powershell
$patterns = @('TB' + 'D', 'FIX' + 'ME', 'fill' + ' in')
foreach ($pattern in $patterns) {
  rg -n $pattern docs/brain
  if ($LASTEXITCODE -eq 0) { exit 1 }
}
"no placeholders"
```

Expected: `rg` finds no matches and the command prints `no placeholders`.

- [ ] **Step 5: Run privacy scan**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .codex\hooks\privacy-scan.ps1
```

Expected: exit code 0.

- [ ] **Step 6: Rebuild Graphify hook if available**

Run:

```powershell
if (Get-Command npx -ErrorAction SilentlyContinue) {
  npx graphify hook-rebuild
} else {
  'npx not available; graphify hook rebuild skipped'
}
```

Expected: either Graphify rebuild completes or the skip message prints. If Graphify generated files change, leave them unstaged unless the repository convention requires committing portable Graphify artifacts.

- [ ] **Step 7: Commit final index/log consistency updates if needed**

Run:

```powershell
git status --short
git add docs/brain/index.md docs/brain/log.md
git diff --cached --quiet
if ($LASTEXITCODE -eq 1) {
  git commit -m "docs: validate llm wiki vault index"
} else {
  'no final index/log changes to commit'
}
```

Expected: either a small consistency commit or the message `no final index/log changes to commit`.

---

## Final Verification

Run after all tasks:

```powershell
git status --short
Get-ChildItem docs\brain -Recurse -Filter *.md | Select-Object FullName
powershell -ExecutionPolicy Bypass -File .codex\hooks\privacy-scan.ps1
```

Expected:

- `docs/brain/` contains the planned vault pages.
- Privacy scan exits 0.
- Only unrelated pre-existing generated files remain unstaged, such as `.graphify` state or `docs/system-workflows.html`.

## Completion Notes

After implementation, report:

- commits created;
- final validation commands and outcomes;
- whether GitHub Project was changed.

This plan does not physically update the GitHub Project board. The vault will create the triage layer first; board mutation can follow as a separate explicit task once the triage page is reviewed.
