# Claim contradictions and explicit drift

Iteration 2.5 adds deterministic consistency state. It never changes Claim content,
verification, confidence or temporal status, and performs no AI/provider calls.

## Detection

Within one owner, normalized subject and predicate must match exactly. Currently
applicable `ACTIVE`, non-refuted claims with overlapping half-open validity intervals
and unequal normalized typed values produce one `VALUE_CONTRADICTION` pair. Proposed,
inconclusive and disputed claims can participate; they do not acquire additional trust.
Future observations/applicability and expired or superseded claims do not participate.
Text normalization uses the existing Claim rules. Equal numeric values such as integer
`1` and decimal `1.00` are equal when units match. Unit conversion, semantic equivalence,
probabilistic conflict resolution and behavioral drift inference are out of scope.
Different units/types remain explicit mismatches, not silently reconciled quantities.

Pairs are ordered by Claim ID and unique per owner/reason. V40 canonicalizes old pairs
and removes duplicate **derived notifications**, preserving an open notification if any
duplicate was open. It does not remove or rewrite any Claim.

Drift reasons are `SOURCE_STALE`, `SOURCE_DELETED`, `VALIDITY_EXPIRED`, and `SUPERSEDED`.
Source checks compare against owner-scoped source versions already registered in
canonical Claims and command-receipt high-watermarks. They do not poll external systems
or guess source age from an arbitrary TTL. Multiple reasons may coexist. Drift is
persisted separately from canonical temporal/verification state.

## Refresh and notification lifecycle

The metadata-only Claim change listener refreshes the owner before the canonical
transaction commits. Owner locking serializes canonical commands, consistency refresh,
notification commands and account deletion. Refresh is idempotent; duplicate events
neither duplicate a pair nor repeatedly count unchanged drift. Transaction rollback
also rolls back the derived state and suppresses metric increments.

Time-based refresh visits up to 100 owners per run in ascending-ID pages, each owner
in a separate short transaction. It wraps to the beginning after the last page;
multiple instances are safe because owner locks serialize reconciliation. Defaults:

- `app.knowledge.consistency-refresh.enabled=true`
- `app.knowledge.consistency-refresh.delay=PT1M`
- `app.knowledge.consistency-refresh.initial-delay=PT1M`

An owner refresh failure is logged with a fixed reason code, without Claim content;
that owner is retried on the next sweep. Persisted Inspector state can lag wall-clock
expiry until its next sweep. Canonical context checks applicability at read time.
No scheduled provider calls or new durable job type are introduced.

Notification status is independent of truth:

- `OPEN`: detected and awaiting attention.
- `ACKNOWLEDGED` / `DISMISSED`: the user handled the notification for those Claim versions.
- `RESOLVED`: the pair no longer meets the detector's criteria, not a declaration of which Claim is true.

A relevant Claim version change or recurrence reopens the same pair and advances its
aggregate version. An unchanged pair preserves acknowledgement/dismissal. Both commands
require the current conflict version and an owner-scoped idempotency key. Receipts store
only its digest and command metadata; replay returns the current conflict without
reapplying an old action. Reusing a key for different input or submitting an obsolete
version returns `409`. Resolved notifications cannot be acknowledged/dismissed.

Context assembly detects contradictions from its canonical snapshot regardless of
notification status, so dismissing a warning never permits conflicting facts into an
answer or decision. Final durable consumer revalidation remains Iteration 2.6.

## Inspector, lifecycle and metrics

See [Memory Inspector API](memory-inspector.md) for routes. Every route uses the
authenticated owner; missing and foreign resources share `404 NOT_FOUND`.
Reads do not write usage, change Claims or silently refresh state.

Knowledge export schema 4 includes conflict versions, drift and command metadata,
excluding idempotency-key digests. Forget cascades both new tables through their Claim
or conflict references. Account deletion explicitly removes them and retains the usual
backup limitation; one owner's export/deletion does not touch another owner.

Aggregate counters `fitnessapp.knowledge.claim.conflict` and
`fitnessapp.knowledge.claim.drift` use only enum-valued `reason` tags. They count newly
persisted/reopened conflicts and new/version-changed drift reasons after commit,
not every poll, actual UI impressions, user identities, source IDs or values.

Core tests cover equality/normalization/ownership, applicability, drift reasons,
transactional refresh, duplicate events, notification/Claim separation, version races,
API owner scoping, dirty V39-to-V40 upgrade, and lifecycle erasure/export.
