# ADR 0010: Durable Jobs and Delivery Recovery

## Context

Time-based recovery alone cannot identify the current claimant, and a database
cannot prove whether an external provider accepted a request before a timeout,
connection loss, or process crash. Reusing an expired claim or automatically
resending an uncertain Telegram delivery can let a stale worker overwrite
newer state or duplicate an external side effect.

## Decision

Provider work is represented by PostgreSQL-backed state before execution.
Durable jobs and Telegram outbox rows have lifecycle status, bounded attempts,
retry time, and a fenced lease:

- PostgreSQL `NOW()` is the authority for claim and expiry decisions.
- Every claim has `lease_owner`, monotonically increasing
  `lease_generation`, `lease_expires_at`, and `claimed_at`.
- Heartbeat, completion, failure, skip, retryable rejection, and late-result
  handling mutate a live row only when owner and generation still match.
- A stale claimant is a no-op and cannot complete or fail a newer claim.

Expired durable jobs return to `PENDING` only while another attempt is
allowed. An expired final attempt becomes terminal `FAILED` with a stable,
non-PII code. Manual retry is a guarded transition from `FAILED` or `SKIPPED`;
it never resets `RUNNING` or `SUCCEEDED`.

Telegram recovery is deliberately more conservative. An expired `SENDING`
claim, timeout, network failure, or provider 5xx has an uncertain external
outcome. Owner-bound work becomes terminal `DELIVERY_UNKNOWN`; anonymous work
is deleted. It is never automatically resent. A user/operator who accepts the
risk creates a new delivery intent and preserves the old audit row.

Idempotency keys and stable source IDs are required where available, but they
do not turn a provider without an exactly-once acknowledgement protocol into
exactly-once delivery. Modulith `event_publication` remains a separate
transactional intent/audit ledger with the recovery rules documented in the
operations runbook.

## Consequences

- A restart can converge durable ledger state without allowing an old worker to
  overwrite a new claimant.
- Durable jobs can retry known internal work; uncertain Telegram delivery is
  surfaced instead of guessed or silently duplicated.
- Provider side effects remain at-least-once/uncertain according to the
  provider contract; callers must tolerate duplicates where no provider
  idempotency guarantee exists.
- Lease owner/generation and payload details are operational internals and are
  excluded from user exports and privacy-sensitive logs.
- `event_publication` is retained as the account-scoped transactional
  event audit/replay ledger.
