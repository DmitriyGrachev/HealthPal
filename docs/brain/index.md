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
