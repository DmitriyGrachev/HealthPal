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
