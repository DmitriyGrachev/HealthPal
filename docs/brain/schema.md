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
