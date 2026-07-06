---
type: topic
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../AGENTS.md
  - ../../BACKLOG.md
  - ../../.github/codex/prompts/review.md
  - ../../.codex/hooks/privacy-scan.ps1
tags:
  - fitnessapp
  - security
  - privacy
---

# Security And Privacy

## What It Knows

FitnessApp treats secrets and user data as private by default. The project must not print, commit, or copy real secrets. Logs must avoid raw Telegram message text, nutrition details, user notes, AI prompts containing user data, OAuth tokens, JWTs, API keys, and FatSecret tokens.

Endpoint changes should check `SecurityConfig` and existing MockMvc security tests. Existing Flyway migrations are immutable; schema changes need new migrations.

## Evidence

- `AGENTS.md`
- `BACKLOG.md`
- `.codex/hooks/privacy-scan.ps1`
- `.github/codex/prompts/review.md`
- `src/main/java/com/fit/fitnessapp/auth/infrastructure/config/SecurityConfig.java`
- `src/test/java/com/fit/fitnessapp/auth/SecurityProtectedEndpointsWebTest.java`
- `src/test/java/com/fit/fitnessapp/CodeHygieneTest.java`
- [[auth]]
- [[telegram]]
- [[nutrition]]

## Contradictions

- Older project history included secret/logging issues that are now represented as resolved backlog work and Done Project cards. New work must preserve the hardening rather than reintroducing old patterns.

## Open Questions

- Should the privacy scan become a formal CI job instead of a local hook?
- Which logs still need structured error codes without leaking payload details?

## Next Actions

- Run `.codex/hooks/privacy-scan.ps1` before committing vault or security-sensitive changes.
- Use MockMvc security tests for endpoint access changes.
- Keep sensitive examples out of `docs/brain/` because the vault is public-safe and committed.
