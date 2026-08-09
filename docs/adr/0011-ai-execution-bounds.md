# ADR 0011: Bounded AI Execution

## Decision

Every AI workflow runs through `AiExecutionGuard`. It enforces a deadline, provider-attempt cap, global and per-user bulkheads, and an hourly PostgreSQL token reservation. Adapters parse one billable provider response; retry policy belongs to the router and durable job worker.

## Consequences

- A slow or failing provider cannot hold an application transaction indefinitely.
- Budget and concurrency failures are typed and observable without exposing provider payloads.
- Shutdown waits briefly for in-flight work and then interrupts it; durable state is the recovery source.
