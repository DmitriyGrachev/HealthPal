# FitnessApp LLM Wiki Vault Design

## Purpose

FitnessApp should have a public-safe, Git-committed LLM wiki vault inspired by Andrej Karpathy's LLM Wiki pattern. The vault is a compiled knowledge layer between raw project sources and future agent conversations. It should let Codex accumulate project understanding over time instead of rediscovering the same context from `src`, docs, GitHub issues, and the Project board on every turn.

The initial vault is for codebase and project knowledge, not private health notes. It may summarize public GitHub issues and committed repository files, but it must not store secrets, raw user health details, private Telegram text, OAuth/JWT/API tokens, FatSecret raw payloads, or AI prompts containing user data.

## Approved Scope

- Location: `docs/brain/`.
- Visibility: public-safe and committed to Git.
- Initial sources:
  - repository docs: `AGENTS.md`, `README.md`, `CONTEXT.md`, `TESTING.md`, `BACKLOG.md`, and `docs/adr/`;
  - current code overview from the modular monolith;
  - GitHub Project board 1 and its issue/PR items for `DmitriyGrachev/HealthPal`.
- Style: Obsidian-compatible Markdown with YAML frontmatter and `[[wikilinks]]`.
- Tooling: no custom CLI scripts in the first implementation; maintenance is rule-driven through `schema.md`.

## Non-Goals

- Do not replace `AGENTS.md`, `CONTEXT.md`, `TESTING.md`, `BACKLOG.md`, or `docs/adr/`.
- Do not build an embedding/RAG system in the first pass.
- Do not mirror full GitHub issue bodies into the vault.
- Do not store personal health journal material or local secrets.
- Do not add Java application behavior as part of the vault initialization.

## Vault Architecture

Create this structure:

```text
docs/brain/
  README.md
  schema.md
  index.md
  log.md
  sources/
  modules/
  topics/
  issues/
  decisions/
  workflows/
```

Responsibilities:

- `README.md`: human-facing explanation of the vault and how to use it.
- `schema.md`: agent-facing rules for ingest, query, lint, page formats, citation/evidence, privacy, and update behavior.
- `index.md`: content-oriented catalog of all wiki pages, grouped by category.
- `log.md`: chronological append-only record of ingests, queries saved back to the wiki, and lint passes.
- `sources/`: summaries of raw sources such as repo docs, GitHub Project state, and selected codebase scans.
- `modules/`: module pages for `auth`, `nutrition`, `workout`, `analytics`, `ai`, `memory`, `telegram`, and shared exception/API boundaries.
- `topics/`: cross-cutting pages such as testing strategy, security/privacy, CI gates, FatSecret integration, scheduling, Spring Modulith, and GitHub project hygiene.
- `issues/`: triage and synthesis over GitHub issues and project cards.
- `decisions/`: lightweight wiki-level decisions and decision summaries; these do not replace ADRs.
- `workflows/`: user/system flows such as nutrition sync, daily insight, Telegram ask, FatSecret profile sync, and workout import.

## Page Conventions

Every page should have YAML frontmatter:

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

Synthetic pages should use these sections unless a page type has a better reason not to:

- `What It Knows`
- `Evidence`
- `Contradictions`
- `Open Questions`
- `Next Actions`

Rules:

- Prefer stable relative file links for repository sources.
- Use GitHub URLs for project and issue evidence.
- Use `[[wikilinks]]` for vault relationships, for example `[[nutrition-module]]`, `[[github-project-triage]]`, and `[[daily-insight-workflow]]`.
- Do not invent certainty. Mark unclear claims as `needs-review` or list them under `Open Questions`.
- If source material contradicts current code, state the contradiction explicitly.

## Initial Page Set

The first vault implementation should create:

```text
docs/brain/README.md
docs/brain/schema.md
docs/brain/index.md
docs/brain/log.md

docs/brain/sources/repo-docs.md
docs/brain/sources/github-project-2026-07-06.md

docs/brain/modules/auth.md
docs/brain/modules/nutrition.md
docs/brain/modules/workout.md
docs/brain/modules/ai.md
docs/brain/modules/memory.md
docs/brain/modules/telegram.md

docs/brain/topics/testing-strategy.md
docs/brain/topics/security-and-privacy.md
docs/brain/topics/ci-and-quality-gates.md
docs/brain/topics/fatsecret-integration.md
docs/brain/topics/spring-modulith.md

docs/brain/issues/github-project-triage.md

docs/brain/workflows/nutrition-sync.md
docs/brain/workflows/daily-insight.md
docs/brain/workflows/telegram-ask.md
```

If the first implementation becomes too large, reduce depth inside individual module pages rather than dropping `schema.md`, `index.md`, `log.md`, or `github-project-triage.md`.

## Workflows

### Ingest

When adding a source:

1. Read the source and identify whether it is raw evidence, already-synthesized documentation, or current code.
2. Write or update a summary under `sources/`.
3. Update affected pages under `modules/`, `topics/`, `workflows/`, `issues/`, or `decisions/`.
4. Update `index.md`.
5. Append an entry to `log.md`.

The first ingest should process repository docs/code overview and GitHub Project board data together, because the board is stale and needs to be interpreted against current code.

### Query

When answering project questions after the vault exists:

1. Read `docs/brain/index.md`.
2. Read relevant vault pages before broad code searches.
3. Use raw sources or Graphify when the vault is insufficient or possibly stale.
4. If the answer produces durable knowledge, offer to save it back into the vault.

### Lint

A manual lint pass should check:

- stale claims;
- broken or dangling wikilinks;
- orphan pages with no index entry;
- GitHub issues that appear done in current code;
- contradictions between code, docs, backlog, and GitHub Project state;
- pages without evidence;
- pages that mention security/privacy-sensitive material too specifically.

Later, if this becomes repetitive, add scripts for link checks and page metadata checks.

## GitHub Project Triage Model

The vault should not treat GitHub Project card status as ground truth. It should synthesize card state against repository evidence.

Initial triage categories:

- `Now`: still-open work that appears valuable and not already implemented.
- `Verify / Close`: old card likely completed or superseded by code/docs.
- `Later`: valid but not urgent.
- `Duplicate / Merge`: overlapping issues that should be consolidated.
- `Blocked`: requires missing product decision, external credential, or real runtime validation.

Known initial observations from the board review:

- CI/CD appears implemented locally via `.github/workflows/ci.yml`, so issue 18 likely belongs in `Verify / Close`.
- FatSecret controller/service/repository and profile sync exist, so issues 1, 8, and 14 need dedupe or verification rather than blind implementation.
- Spring Modulith dependencies, events, listeners, and architecture tests exist, so issue 7 needs verification against the exact outbox expectation.
- Nutrition sync and daily insight scheduling remain the most likely active workflow area to inspect next.

## Evidence Priority

When sources disagree, prefer:

1. current code and tests;
2. committed ADRs and stable docs;
3. current GitHub Project metadata;
4. GitHub issue bodies;
5. old backlog text;
6. agent conversation memory.

The vault should record the disagreement instead of silently choosing one source.

## Safety Rules

- Never store real secrets, local `.env` values, private tokens, raw Telegram message text, raw nutrition logs, raw AI prompts with user data, OAuth/JWT/API values, FatSecret raw payloads, or personal health journal notes.
- Summarize sensitive workflows structurally, not with private examples.
- If a source contains sensitive values, do not copy them. Record only that the source was excluded or redacted.
- Keep the vault public-safe because it is committed to the repository.

## Verification For First Implementation

Before committing the initial vault:

1. Check file list under `docs/brain/`.
2. Check Markdown pages for obvious placeholders, broken headings, and inconsistent frontmatter.
3. Run the repository privacy scan path or at least inspect staged content for likely secrets.
4. Run `git status --short` and stage only the intended vault files.
5. Commit the vault separately from unrelated generated Graphify state.

## Acceptance Criteria

- `docs/brain/` exists with the approved structure.
- `schema.md` clearly tells future agents how to maintain the vault.
- `index.md` lists every initial page with one-line summaries.
- `log.md` contains the initial ingest entry.
- GitHub Project state is summarized in `sources/github-project-2026-07-06.md`.
- `issues/github-project-triage.md` gives actionable next/verify/later grouping.
- Module/topic/workflow pages cite source files or GitHub URLs.
- No private or secret material is committed.
