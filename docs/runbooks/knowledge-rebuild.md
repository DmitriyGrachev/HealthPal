# Knowledge projection recovery

Scope: the versioned `KNOWLEDGE_CLAIM` pgvector projection, not canonical Claims, source records,
or legacy memory documents. PostgreSQL remains authoritative. Rebuilding never confirms a Claim,
changes an Experiment, restores forgotten content, or records Claim usage.

## Preconditions and request

1. Confirm the account still exists and resolve its ID through an authenticated application boundary.
2. Keep the existing database, migrations and durable worker configured. Do not truncate Claims,
   generation metadata, receipts or jobs to repair a missing vector index.
3. Embedding requires the existing sensitive-egress permission and configured provider. Do not enable
   egress just to make a failed job green; canonical inspection and evaluation work without it.
4. From a trusted application integration, call the existing
   `MemoryProjectionRebuildUseCase.request(ownerId, idempotencyKey)`. Retrying that request with the
   same owner/key returns the existing job. There is no new public rebuild endpoint or SQL bypass.
5. Let the durable worker claim `MEMORY_PROJECTION_REBUILD`. The executor requires the current
   `DurableJobClaim`, including lease generation; invoking it with an unfenced job DTO is rejected.

The job payload is `{}`. Never put Claim text, provider credentials or a prompt in the job key/payload.
The job ID returned by the request is the recovery handle, not a success confirmation.

## What the worker does

```text
owner + current job lease
  -> BUILDING generation based on current ACTIVE
  -> release transaction -> guarded embeddings -> fenced vector writes
  -> owner + current lease + exact current Claim manifest + base-generation CAS
  -> ACTIVE replacement and old vector deletion in one transaction
```

The canonical manifest is sorted by Claim ID and contains source identity/version, aggregate
version, content hash and schema version. Its eligibility rule is active, not disputed/refuted,
and valid at the current time. Vector text remains a semantic projection, not verified truth.
Context consumers separately apply purpose, current source receipts, conflicts and trust rules.

Normal Claim commands serialize on the owner: a newer same-source Claim supersedes its predecessor;
source deletion removes all its Claims; forget removes the connected lineage and fences replay.
V39 synchronously invalidates vectors on Claim updates/deletion and rejects delayed writes with
an obsolete Claim identity or retired/missing generation. Source receipts alone are not a generic
external-source change feed. Do not manufacture them with SQL or claim detection of unobserved changes.

An empty allowed set can activate without calling a provider. Two builds based on the same active
generation cannot both activate. A lost lease cannot publish a replacement. Failure rolls back
activation, cleans the failed build's vectors and preserves the previous active generation.
Deleting only versioned vector rows leaves canonical Claims available for a normal rebuild; such
manual deletion is not required for recovery and is not performed by this runbook.

## Observe and recover

Inspect only the owned job and generation metadata. Useful columns are job status/attempts/
`next_retry_at`, and generation ID/status/`failure_code`/timestamps. Never export lease owner tokens,
serialized publications or embedding arrays for diagnosis. Aggregate metrics are documented in
[knowledge-quality.md](knowledge-quality.md).

| Result | Action |
|---|---|
| `SUCCEEDED`, expected generation active | Verify the owned source manifest; no further work. |
| `EGRESS_DENIED` | Keep prior projection; review authorized configuration, or continue canonical-only. |
| `WRITE_FAILED` | Check provider/database availability without logging payloads; use normal job retry. |
| `SOURCE_CHANGED` | Canonical data changed during build; retry from current truth, never reuse staged vectors. |
| `FENCE_REJECTED` | Claim/lease or base generation lost; inspect the winning job/build before creating new intent. |
| `TRANSACTION_FORBIDDEN` | Fix the caller boundary; embedding must not run inside a database transaction. |
| Owner removed | Stop. Account deletion is not a retryable rebuild request. |
| Expired final job attempt | Durable recovery reaches `FAILED`; explicit authorized retry may create a fresh claim. |

Normal durable-job retries use owner/generation fencing. Retry only terminal retryable jobs through
the existing owned job API; do not reset a live claim or edit a generation to `ACTIVE`. A new request
key represents a new intent. `REPLACED` on a retired generation is normal, not a provider failure.

A hard process crash may leave an invisible `BUILDING` generation. Retry builds a new generation;
the abandoned one cannot activate using an expired lease. There is no orphan-generation sweeper:
abandoned metadata/vector storage remains until account deletion. This is a known release
limitation, not a claim that every crash cleans staging immediately.

## Export, erasure and replay

- Knowledge export schema 5 covers Claims, evidence, safe command receipts, exact usage snapshots,
  conflicts, conflict-command receipts, drift, generation metadata and content-free deletion receipts.
- Memory export schema 2 includes owned projection content/identities, never embedding arrays.
- The jobs participant exports safe owned rebuild-job metadata, without active lease secrets.
- Account deletion locks the owner, deletes module-owned data and jobs in one transaction, then
  deletes the identity. Owner/Claim/generation foreign keys and write fences prevent delayed replay
  from recreating Claims or vectors. Another account's data and global budgets remain untouched.
- Forget removes the Claim lineage, usage and vectors. Pending identifiers-only events may still
  replay, but reread absence and cannot restore projection content. Rebuild jobs use the remaining
  canonical set. Live-account hashed source/key fences prevent Claim recreation; account deletion
  removes those fences and outstanding owned publications/jobs.
- FatSecret disconnect is not account deletion: it clears provider data and vectors, but independent
  canonical Claims survive. Rebuild never fetches FatSecret or promotes provider content into evidence.
- Local erasure does not promise deletion at an AI provider or in independently retained backups.

## Verification

`KnowledgeProjectionIntegrationTest`, `MemoryProjectionRebuildIntegrationTest`,
`KnowledgeClaimPersistenceIntegrationTest`, `HistoricalUpgradeIntegrationTest` and
`UserDataLifecycleServiceIntegrationTest` cover the recovery, immutable upgrade, owner isolation,
forget and account-deletion fences with PostgreSQL and mocked embeddings. Run the full release
commands recorded in the Phase 2 plan before declaring the phase complete.
