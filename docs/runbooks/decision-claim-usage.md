# Exact Claim usage for Experiment Decisions

Iteration 2.6, Decision contract. AI consumer integration is documented in
[ai-answer-claim-usage.md](ai-answer-claim-usage.md); current gate evidence is in the
[Phase 2 plan](../superpowers/plans/2026-08-12-phase-2-trustworthy-context.md).

## Request contract

`POST /api/v1/experiments/{experimentId}/decision` accepts the existing fields plus optional
`claimsUsed`, a list of `{claimId, version, contentHash}`. Use the canonical Claim identity
from the Inspector or purpose-scoped context, not a source document's hash. At most 20
unique Claim IDs are accepted. Ordering does not affect idempotency. The authenticated
owner is server-derived; references never carry their own owner or Claim text.

Missing/null/empty `claimsUsed` preserves an explicit manual Decision and records no
Claim usage. Merely retrieving context never counts as using it. The client must submit
only the knowledge the user selected as the basis for that Decision.

The context owner locks the user and rechecks each selected Claim's owner, version,
hash, active applicability, observation time, supported verification, current source
high-watermark/deletion state, and current contradictions. Dismissing a conflict notice
does not resolve that contradiction. An AI-origin Claim additionally requires explicit
user confirmation; model confidence alone is insufficient.

Rejected context returns HTTP 409 / `DECISION_CONTEXT_REJECTED`, without foreign-owner
details. Refresh/review the selected knowledge. A manual choice without Claim references
is still possible; it does not claim support from disputed or unconfirmed knowledge.

## Transaction and replay

```text
validate selected identities + lock owner
  -> insert Decision -> reserve command receipt -> snapshot Claim usage -> commit
```

Every write joins the same transaction. A rollback leaves no Decision, command receipt,
usage record or usage metric increment. The owner lock prevents correction, forgetting,
source changes or account deletion between validation and the usage snapshot. There is
no provider I/O in this transaction.

The existing command fingerprint includes the canonical sorted references. A changed
selection with the same key conflicts; another key with an identical selection converges.
An existing Decision's original fingerprint is read from its durable command receipts.
Legacy Decisions without a receipt can only converge under the legacy no-Claim contract.
Replays return the stored Decision and never recreate usage erased by forgetting a Claim.

`knowledge -> experiment::api` implements the consumer-owned `DecisionContextUsage` port.
Experiment imports no knowledge implementation and stores no Claim foreign key or copied
content. Knowledge records `experiment-decision:<id>` as its consumer identifier.

## History, export and deletion

V41 adds `claim_version` and `claim_content_hash` to usage records. New records snapshot
both from the canonical owner-scoped Claim. Existing rows remain null/unknown: current
Claim state cannot establish which historical version was used. These fields are not
rewritten when the Claim later changes.

Knowledge export schema 5 includes both identity fields. Existing owner/Claim cascades
and lifecycle cleanup remain in force; forget removes associated usage and account
deletion removes it all. Command fingerprints retain no plaintext Claim content.

The core workflow covers atomic rollback, exact identities, replay, altered selection,
forget and account deletion. Existing web/lifecycle/upgrade tests cover binding, safe
409 responses, export and legacy unknown identities; one focused guard test covers trust,
ownership, stale sources/versions, changed hashes and contradictions.
