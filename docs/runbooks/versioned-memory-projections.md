# Versioned memory projections

Iteration 2.4 implements a rebuildable pgvector projection of canonical KnowledgeClaims.
PostgreSQL claims remain authoritative. Projection text is always `SEMANTIC`, never a
verified fact; legacy Goal-note projections are `EPISODIC` with their existing retention.
V39 removes old Goal/AI-insight `FACT` vectors without removing canonical source data.

## Writes and rebuild

The metadata-only Claim event listener rereads the owned current source version,
aggregate version and content hash. Inactive, disputed, refuted and temporally invalid
claims are excluded. Identical events reuse a deterministic vector ID and avoid repeated
embedding when that source is already indexed in the active generation.

`MemoryProjectionRebuildUseCase.request(owner, idempotencyKey)` queues
`MEMORY_PROJECTION_REBUILD`. This is an internal API, not a new public endpoint.
The existing durable worker supplies its lease owner/generation to the executor;
invocation with only a job DTO is rejected. Keep owner resolution at the authenticated
consumer boundary when wiring consumers in Iteration 2.6.

1. Lock the owner, validate the current job lease, and create `BUILDING` from the active generation.
2. Release the transaction, then classify sensitive egress before each embedding request.
3. Write vectors carrying owner, claim/source IDs, source and aggregate versions, hash and generation.
4. In a short transaction, lock owner then job, verify the lease and exact current canonical source set,
   and compare-and-swap the base generation. Only one concurrent replacement can activate.
5. Retire the old generation as `FAILED` / `REPLACED` and remove its vectors in that same transaction.

Failure or egress denial cleans that build's vectors and leaves the previous active
generation intact. Failures expose stable reason codes, not provider payloads. An empty
source set needs no provider call and can activate an empty generation. Account removal
also removes a bootstrap empty active generation if one was created before a failed first build.

The job's ordinary retry/recovery policy still applies. A hard process crash can leave
an invisible `BUILDING` generation: a retry creates a new generation, and stale workers
cannot activate without their valid lease. Such orphan metadata/vectors currently remain
until account deletion; there is no background generation-retention sweeper in this iteration.

## Read and privacy boundaries

The optional `ContextNarrativeSearch` adapter searches only the owner's active generation
and proposed/inconclusive claims. It returns IDs, versions and hashes, not authoritative
text. The context policy rereads and checks canonical content. Evaluation requests never
query this projection. Legacy memory readers exclude all versioned projection documents,
including staging generations. Search failure degrades to canonical-only context.

Embedding and search run outside database transactions and behind `SensitiveAiEgressGuard`.
No real providers are used by tests. A denied event remains a failed publication for
normal retry after configuration changes; it does not roll back its committed claim.
`app.memory.knowledge-events-enabled=false` disables the automatic listener, not the
egress guard or the explicit rebuild API.

The database fences a delayed vector write against missing owners, retired generations
and changed/deleted claims. Claim mutation invalidates every generation synchronously;
forget and account deletion cascade to vectors. This does not depend on eventual event delivery.
Expiry is also enforced at retrieval and on rebuild; no new expiry sweeper is added.

Knowledge export schema 5 includes generation metadata without embeddings, job leases
or claim text in that metadata. Memory export schema 2 includes existing text/metadata
and source/generation identifiers, never embedding arrays. Account lifecycle deletes
generations and vectors; external-account disconnect retains the existing vector-clearing
behavior. Local erasure does not promise provider-side or backup erasure.

## Verification

Core PostgreSQL tests cover denial without embedding, replay/version/forget fencing,
partial build failure, canonical manifest equality and narrative search, deletion during
embedding, and concurrent activation. Historical-upgrade and lifecycle cases cover V39
cleanup, opaque legacy metadata compatibility, export and owner isolation.

Run `mvn verify -Pintegration` and `mvn test -Parchitecture` for this cross-module schema change.
Claim-usage writes belong to durable consumers, never projection retrieval/rebuild. Iteration 2.6
integrates those consumers; see [AI answer usage](ai-answer-claim-usage.md),
[Decision usage](decision-claim-usage.md) and the current [recovery runbook](knowledge-rebuild.md).
