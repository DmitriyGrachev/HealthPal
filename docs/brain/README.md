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
