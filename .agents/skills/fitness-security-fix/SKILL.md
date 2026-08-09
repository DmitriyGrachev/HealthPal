---
name: fitness-security-fix
description: Guides FitnessApp security and privacy fixes across Spring Security, Telegram logging, AI/FatSecret secrets, dependency CVEs, and security backlog items. Use when working on BACKLOG.md security fixes, auth matchers, role gates, secret handling, privacy-safe logs, vulnerable dependencies, or security-oriented tests.
---

# Fitness Security Fix

## Workflow

1. Read the relevant `BACKLOG.md` item, affected files, and existing tests. Treat garbled text as a clue, not proof.
2. Reproduce or confirm the current behavior with the narrowest test or code inspection.
3. Add or update a focused test before changing production code when the behavior is testable.
4. Make the smallest code change that satisfies the security rule.
5. Run the narrow test first, then `mvn test` unless a broader gate is clearly required.

## Security Defaults

- Check `src/main/java/com/fit/fitnessapp/auth/infrastructure/config/SecurityConfig.java` before changing endpoint access.
- Prefer MockMvc tests in `src/test/java/com/fit/fitnessapp/auth/SecurityConfigWebTest.java` for role gates and anonymous access.
- Require explicit roles for batch or all-user endpoints such as analytics generation and imports.
- Do not rely on path patterns until they match the real controller paths.

## Privacy Defaults

- Never log raw Telegram message text, user notes, nutrition details, prompt bodies, OAuth tokens, JWTs, API keys, or FatSecret tokens.
- Log metadata instead: `chatId`, command type, handler, status, exception class, and stable error code.
- Use `CapturedOutput` tests when fixing logging leaks.

## Dependency Fixes

- Verify actual dependency versions with `mvn dependency:tree`.
- Keep dependency upgrades narrow unless the backlog item explicitly asks for a larger migration.
- After Spring AI, Spring Boot, Tomcat, or JJWT changes, run the affected tests and `mvn test`.

## Acceptance

Before finishing, report:

- The concrete behavior protected.
- The exact test command run.
- Any remaining risk, skipped broader gate, or dependency migration follow-up.
