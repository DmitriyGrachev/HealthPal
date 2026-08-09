# Keep Batch Report Endpoints Admin-Only

Weekly Report and Monthly Report batch workflows operate across many Users, so they are administrative operations even when they produce per-user AI Insights. Web endpoints that trigger these all-user workflows should be restricted to admin access, while ordinary Users should use current-user flows that cannot process other Users' data.

## Considered Options

- Let any authenticated User trigger the batch endpoint, which is convenient but exposes cross-user work and expensive AI generation.
- Remove web-triggered batch endpoints entirely and rely only on scheduling, which is safer but makes manual recovery harder.
- Keep the endpoints for operations and recovery, but require admin access.

## Consequences

Security tests should prove that ordinary Users cannot trigger all-user report generation. If a user-facing report endpoint is needed later, it should call a current-user use case rather than reusing the batch orchestrator directly.
