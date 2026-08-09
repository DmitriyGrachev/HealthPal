---
type: module
status: current
owner: codex
updated: 2026-07-06
sources:
  - ../../AGENTS.md
  - ../../CONTEXT.md
  - ../../src/main/java/com/fit/fitnessapp/auth/
  - ../../src/test/java/com/fit/fitnessapp/auth/
tags:
  - fitnessapp
  - module
  - auth
---

# Auth Module

## What It Knows

Auth owns user identity, registration, login, JWT infrastructure, current-user resolution, user notes, and security configuration. It exposes public user/current-user APIs to other modules while keeping persistence and controller details inside the module.

Personal workflows should resolve the current user through auth boundaries rather than accepting arbitrary user IDs from controllers. Operational all-user workflows are treated separately and require stronger security gates.

## Evidence

- `src/main/java/com/fit/fitnessapp/auth/CurrentUserApi.java`
- `src/main/java/com/fit/fitnessapp/auth/UserApi.java`
- `src/main/java/com/fit/fitnessapp/auth/infrastructure/config/SecurityConfig.java`
- `src/main/java/com/fit/fitnessapp/auth/infrastructure/utils/JwtCore.java`
- `src/main/java/com/fit/fitnessapp/auth/application/service/CurrentUserService.java`
- `src/main/java/com/fit/fitnessapp/auth/application/service/UserNoteService.java`
- `src/test/java/com/fit/fitnessapp/auth/SecurityProtectedEndpointsWebTest.java`
- `src/test/java/com/fit/fitnessapp/auth/AuthControllerTest.java`
- `src/test/java/com/fit/fitnessapp/auth/UserNoteControllerValidationTest.java`
- [[security-and-privacy]]
- [[spring-modulith]]

## Contradictions

- GitHub Project issue #12 asked to separate user and auth into modules, but the Project board marks it Done. Treat further auth/user splitting as a new architecture decision, not as unfinished old work.

## Open Questions

- Should user notes remain inside auth long-term, or become a separate user/profile module if note behavior grows?
- Are there remaining endpoints that still accept explicit user IDs for personal workflows?

## Next Actions

- Use `SecurityConfig` and MockMvc security tests as required context for endpoint changes.
- Keep auth-facing DTO validation covered by web tests.
- Do not import internal auth persistence packages from other business modules.
