# ADR 0013: Stable Base Operations

## Decision

Production-like and dev profiles use PostgreSQL/Flyway validation, UTC scheduler policy, restart replay for outstanding Modulith publications, bounded executor shutdown, and aggregate Actuator gauges for jobs, outbox, and event publications. Operational procedures live in `docs/runbooks/stable-base-operations.md`.

## Consequences

- The three Maven gates and clean-profile smoke test are release prerequisites.
- Alerts use aggregate counts only; personal data and provider payloads are never metric tags or log fields.
- Vector approximate indexing stays disabled until a production-shaped benchmark selects a compatible index strategy.
