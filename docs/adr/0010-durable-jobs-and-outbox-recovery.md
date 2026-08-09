# ADR 0010: Durable Jobs and Delivery Recovery

## Decision

Provider work is represented by PostgreSQL-backed state before execution. Durable jobs and Telegram outbox rows have an idempotency key, lifecycle status, bounded attempts, retry time, claim/recovery timestamps, and an operator retry path. Delivery is at-least-once because Telegram does not provide an exactly-once acknowledgement protocol.

## Consequences

- A process restart can recover `RUNNING`/`SENDING` rows without guessing whether a request was accepted.
- Duplicate provider effects must be tolerated by idempotency keys and stable source IDs.
- `event_publication` is retained as the transactional event audit/replay ledger.
