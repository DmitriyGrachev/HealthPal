# ADR 0016: Commit Source State and Durable Intent Atomically

## Status Note

ADR 0015 supersedes the provider-backed nutrition canonical/source-state/event
path described below. This decision remains active for workout state and its
derived projections.

## Context

Nutrition and workout changes drive a derived DAILY insight. A listener may be
delivered late, duplicated, or replayed after a newer source change or account
deletion. Canonical rows, source identity, the Modulith publication receipt,
and the derived insight therefore cannot be treated as independent last-write-
wins updates.

## Decision

Each nutrition/workout calendar date has module-owned source state containing a
monotonic version, presence/tombstone flag, content hash, schema version, and
account lifecycle epoch. The producer coordinator writes canonical data,
advances that state, and publishes its metadata-only event in one PostgreSQL
transaction. The Modulith publication row is the durable intent; no separate
outbox write is introduced.

All canonical source mutation and DAILY projection snapshot/commit paths use
the neutral `UserDateTransactionLock`. Its PostgreSQL adapter exclusively locks
and reads the owning `users` row before acquiring one transaction-scoped
advisory lock from a stable namespace, user ID, and date. This global order lets
a multi-date producer extend its sorted date fence without deadlocking a
single-date projection. Producers acquire this fence before canonical mutation.
Snapshot capture acquires it before reading canonical data and both typed
source-state optionals. Final DAILY save or delete reacquires the same fence and
compares the exact lifecycle epoch plus both optionals inside the persistence
transaction. Explicit absence is part of the comparison, so a new source row
cannot be missed.

Account deletion follows the same order: it locks the user identity before any
module participant deletes child rows. Deletion therefore either removes the
owner before a projection can observe it or waits behind an already-fenced
source/projection transaction without creating a child-row/owner-row cycle.

Consumers accept only complete metadata matching the event envelope and the
module-owned current state. Legacy, missing-owner, malformed-identity, future,
or stale events complete as no-ops. A current UPSERT or DELETE creates a
versioned durable job whose identity contains the stable source identity,
source version, and event ID. The job carries optional typed trigger metadata;
legacy date-only jobs remain readable. A trigger or final snapshot that is no
longer current returns `SKIPPED_STALE` without retrying the AI provider or
persisting/publishing an obsolete projection.

Incomplete Modulith publications are replayed through the Spring Modulith
1.1.3 `IncompleteEventPublications` API. Completion remains update-in-place;
the restart-republication property is unchanged. Publication lifecycle export
uses the enforced `user_id` owner column and an allowlist of receipt fields:
identifier, listener/event type, publication timestamp, and completion
timestamp. Serialized event content is never exported. Receipts are retained
for the account lifetime and deleted by owner/FK cascade.

## Consequences

- Duplicate and out-of-order delivery converges from current source truth and
  cannot restore an older payload.
- Source changes during provider work cannot leave the older DAILY projection
  as the committed final state.
- Account deletion either completes before a projection observes the owner or
  waits for the fenced transaction and then cascades every owned effect.
- Correctness depends on all nutrition/workout canonical mutation paths using
  their coordinator and on PostgreSQL transaction/advisory-lock semantics.
- The user-row lock deliberately favors correctness over same-account write
  concurrency; provider calls remain outside every fenced transaction.
