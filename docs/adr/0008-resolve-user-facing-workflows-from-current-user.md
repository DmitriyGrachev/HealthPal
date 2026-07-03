# Resolve User-Facing Workflows From Current User

User-facing web and Telegram workflows should derive the acting User from authentication or Telegram Link state. A request should not be able to choose another User id for personal nutrition, workout, note, memory, or AI workflows.

## Considered Options

- Accept `userId` parameters in user-facing controllers, which makes manual testing easy but creates authorization hazards.
- Resolve the Current User server-side, which requires auth/link plumbing but keeps ownership checks local and consistent.

## Consequences

Controllers for personal workflows should use `CurrentUser` or Telegram Link state before calling use cases. Endpoints that intentionally operate across many Users are administrative batch workflows and must follow ADR-0002.
