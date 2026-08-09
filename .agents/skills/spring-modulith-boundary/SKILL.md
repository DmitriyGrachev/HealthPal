---
name: spring-modulith-boundary
description: Diagnoses and fixes FitnessApp Spring Modulith module boundary failures, cycles, exposed API issues, and architecture profile regressions. Use when mvn test -Parchitecture fails, when changing cross-module dependencies, or when moving events, exceptions, ports, and public APIs between modules.
---

# Spring Modulith Boundary

## Workflow

1. Run or inspect `mvn test -Parchitecture` to capture the exact violation.
2. Use Graphify before broad file reading:
   - `graphify summary --graph .graphify/graph.json`
   - `graphify query "<module cycle or dependency question>" --graph .graphify/graph.json`
3. Identify the dependency direction that violates the intended module boundary.
4. Prefer stable public contracts over direct internal imports.
5. Add or adjust focused tests when the boundary change has behavior, then re-run the architecture gate.

## Boundary Patterns

- Put cross-module events in exposed API packages when multiple modules publish or consume them.
- Use ports for dependencies that point inward to application behavior.
- Keep framework and exception translation out of domain packages.
- Avoid making `exception` a module that imports feature-module internals.
- Prefer narrow `@NamedInterface` exposure over making whole packages public.

## Common FitnessApp Hotspots

- `ai` and `analytics` interactions around report events.
- `exception` imports of feature-specific exception classes.
- `nutrition`, `auth`, and `analytics` cycles through events or shared handlers.
- Controller packages depending on persistence internals.

## Acceptance

The work is complete only when:

- `mvn test -Parchitecture` passes, or the remaining violation is explicitly documented.
- The final explanation names the dependency direction that was removed.
- No unrelated package reshuffle was introduced.
