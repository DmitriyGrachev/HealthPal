# Phase 2 Trustworthy Personal Context — implementation plan

> Execute after the Debugger Alpha **engineering-readiness** gate in the [`Stage 2 canonical roadmap`](../specs/2026-08-12-stage-2-roadmap-design.md) is green. Product-validation evidence continues in parallel and is never inferred from automated tests. Each numbered iteration is reviewed and committed independently.

**Goal:** add a user-visible, explainable personal knowledge layer whose PostgreSQL source of truth survives provider replacement, vector deletion, replay, and account lifecycle operations.

## Execution checkpoint — 2026-09-03

- Iteration 2.1 is committed as `49035b1`.
- Iteration 2.2 is committed as `0420289`; API contract: [Memory Inspector runbook](../../runbooks/memory-inspector.md).
- Iteration 2.3 is committed as `6e507eb`; contract and policy: [Purpose-scoped context runbook](../../runbooks/purpose-scoped-context.md).
- Iteration 2.4 is committed as `bcf3e7c`: [Versioned memory projections](../../runbooks/versioned-memory-projections.md).
- Iteration 2.5 is implemented and verified inline, with no subagents: [Claim consistency contract](../../runbooks/claim-consistency.md).
- Next: Iteration 2.6, Experiment/AI integration and the Phase 2 exit gate. Phase 2 is not yet complete.
- Verification for 2.5: `mvn verify -Pintegration` passed (650 fresh default tests, 138 PostgreSQL tests); architecture 11 tests with one intentional skip; dependency analysis, privacy and diff checks passed. The known post-`System.exit(0)` integration-fork warning recurred; Maven exited 0.
- Eight core cases added: three detector tests, three transactional consistency cases, one MockMvc command case and one dirty historical upgrade case. Existing lifecycle tests cover both new tables. Numeric equality was RED before correction (`1` and `1.00`); one context fixture was corrected to model independent predicates rather than accidental contradictions.
- Primary review confirmed owner/version/idempotency fencing, atomic canonical refresh, read-only context protection despite dismissal, metadata-only exports and enum-only metrics. Source staleness uses registered local high-watermarks, not external polling; wall-clock expiry refresh is paged and eventually consistent. No automatic Claim correction or trust promotion.
- Graphify rebuilt for 2.5 (4791 nodes, 8955 edges); generated artifacts remain excluded. The privacy hook was run separately because Graphify's PowerShell parser is unavailable.
- Verification for 2.4: `mvn test` and `mvn verify -Pintegration` passed (646 fresh default tests, 134 PostgreSQL tests); `mvn test -Parchitecture` passed (11 tests, one intentional skip). Dependency analysis, privacy scan and diff check passed. The known integration-fork shutdown warning recurred after all tests passed; Maven exited 0.
- Added nine core test cases (including one extra parameterized case); existing lifecycle/lease tests were extended. Primary inline review checked lease/source fencing, active-generation reads, egress outside transactions, owner deletion, migration compatibility and module boundaries. No live provider calls. Orphan crash-time BUILDING retention is explicitly documented, not claimed solved.
- Graphify rebuilt for 2.4 (4674 nodes, 8776 edges); generated files remain local and excluded. Its unavailable PowerShell parser does not replace the separately executed privacy hook.
- Verification for 2.3: `mvn verify -Pintegration` passed (643 default tests from fresh reports, 128 PostgreSQL tests); architecture 11 tests with one intentional skip; dependency analysis, privacy scan and diff check passed. The integration fork emitted a 30-second post-`System.exit(0)` shutdown warning; Maven exited 0 and all test results passed.
- Added six core tests. The full gate exposed an old explicit-user-ID/identity-sequence collision in two test fixtures; corrected only those fixture helpers and reran the full gate successfully. No live AI/provider calls. Primary review retained distinct canonical facts from one source while collapsing repeated optional AI evidence.
- Graphify rebuilt (4595 nodes, 8649 edges); its PowerShell parser is unavailable for the privacy hook, which was executed separately. Generated Graphify files remain local and are excluded from the feature commit.

## Domain contract

A `KnowledgeClaim` is canonical business state. It separates three dimensions that the older review mixed together:

```text
ClaimOrigin:
  USER_DECLARED | IMPORTED | SYSTEM_DERIVED | AI_HYPOTHESIS | EXPERIMENT_RESULT

ClaimVerification:
  PROPOSED | SUPPORTED | REFUTED | INCONCLUSIVE | DISPUTED

ClaimTemporalStatus:
  ACTIVE | EXPIRED | SUPERSEDED
```

An AI hypothesis cannot become `SUPPORTED` because another AI output repeats it. Correction creates a new Claim and supersedes the old one. Forget physically deletes Claim content, evidence links, usage links, conflicts, and vector projection; only a non-content deletion receipt may remain.

The deterministic contradiction MVP is: two `ACTIVE` claims with the same normalized subject and predicate, overlapping validity windows, and different normalized values produce an open conflict. The system shows the conflict and abstains; it never silently chooses a winner.

## Iteration 2.1 — Canonical typed KnowledgeClaim

### RED

Add:

- `src/test/java/com/fit/fitnessapp/knowledge/domain/KnowledgeClaimTest.java`
- `src/test/java/com/fit/fitnessapp/knowledge/domain/TypedClaimValueTest.java`
- `src/test/java/com/fit/fitnessapp/knowledge/application/service/KnowledgeClaimServiceTest.java`
- `src/test/java/com/fit/fitnessapp/knowledge/adapter/out/persistence/KnowledgeClaimPersistenceIntegrationTest.java`

Prove typed values/units, temporal validity, origin/verification separation, normalized content hash, exact source-version idempotency, owner isolation, correction-as-supersession, and cascade deletion.

### GREEN

Add `src/main/resources/db/migration/V37__create_knowledge_claims.sql` with:

- `knowledge_claims`;
- `knowledge_claim_evidence`;
- `knowledge_claim_command_receipts`;
- owner FK `users(id) ON DELETE CASCADE`;
- composite ownership FKs;
- JSON value shape checks plus explicit `value_type` and optional unit;
- source type/id/version, observed time, validity interval, content hash, schema version;
- self-FK for supersession;
- uniqueness on owner plus canonical source/version/hash;
- indexes for active subject/predicate and freshness queries.

Add module structure:

- `knowledge/package-info.java`;
- `knowledge/api/package-info.java` for identifiers-only events and the later `context-api` named interface;
- `knowledge/spi/package-info.java` exposed as `projection-spi`, implemented by memory without knowledge importing memory;
- domain types `KnowledgeClaim`, `ClaimSubject`, `ClaimPredicate`, `TypedClaimValue`, `ClaimOrigin`, `ClaimVerification`, `ClaimTemporalStatus`, `ClaimSourceRef`, `ClaimEvidence`, `ClaimConfidenceBasis`;
- `KnowledgeClaimCommandUseCase`, `KnowledgeClaimQueryUseCase`;
- repository ports, `KnowledgeClaimService`, and JDBC persistence adapter;
- versioned `KnowledgeClaimChangedEvent` containing identifiers/hash only, never Claim value text.

Declare the initial `knowledge` module with `@ApplicationModule(allowedDependencies={"api::lifecycle"})`. Architecture tests forbid broad `api`, `ai`, `memory`, and implementation-package dependencies. Iteration 2.2 adds the narrow `auth::current-user` interface for authenticated inspector controllers, as required by the canonical current-user invariant. Later iterations add `experiment::query-api` and `experiment::api` when those consuming adapters/events are introduced.

Every command uses current owner, `expectedVersion`, and `idempotencyKey`. A source replay with an older/equal version is a successful no-op; a deleted source cannot resurrect a Claim.

Add `KnowledgeUserDataLifecycleParticipant` in the same iteration and extend V2 export plus `UserDataLifecycleServiceIntegrationTest` for claims, evidence, and command receipts before committing.

Add `knowledge.claim.created` metrics labeled only by stable origin/verification enums; metric tests reject user IDs and Claim content as tags.

### Verify and commit

```powershell
mvn -Dtest=KnowledgeClaimTest,TypedClaimValueTest,KnowledgeClaimServiceTest test
mvn -Dit.test=KnowledgeClaimPersistenceIntegrationTest,UserDataLifecycleServiceIntegrationTest verify -Pintegration
mvn test -Parchitecture
```

Commit: `feat: add canonical typed knowledge claims`

## Iteration 2.2 — Memory Inspector, provenance, and claim usage

### RED

Add MockMvc and integration tests for:

- current-user list/detail filtering;
- visible source, evidence, observed/validity times, origin, verification, status, and supersession history;
- confirm, dispute, correct, and forget;
- correction preserving history while only the new Claim stays active;
- forget deleting content and all projection work;
- recording which Experiment Decision or AI answer used a Claim;
- no cross-user ID probing;
- unauthenticated access, validation bounds, optimistic conflict, and idempotency behavior for every Memory Inspector route.

### GREEN

Add `src/main/resources/db/migration/V38__create_knowledge_claim_usage_and_conflicts.sql` with:

- `knowledge_claim_usage`;
- `knowledge_claim_conflicts`;
- `knowledge_deletion_receipts` containing live owner, deleted claim ID, deletion time, and schema version but no value/source content; `owner_id` has `users(id) ON DELETE CASCADE`. Internal owner-scoped source/key digests provide replay fencing without retaining plaintext source identifiers or command keys; these digests are not part of exported audit metadata.

Add:

- `KnowledgeClaimController`;
- `ClaimConflictController` query endpoint initially returning persisted open conflicts;
- `ClaimUsageRecorder` port/service;
- response DTOs that expose provenance without provider secrets.

Routes:

```text
GET    /api/v1/knowledge/claims
GET    /api/v1/knowledge/claims/{id}
POST   /api/v1/knowledge/claims/{id}/confirm
POST   /api/v1/knowledge/claims/{id}/dispute
PUT    /api/v1/knowledge/claims/{id}
DELETE /api/v1/knowledge/claims/{id}
GET    /api/v1/knowledge/conflicts
```

Correction is a transaction that locks the old Claim, inserts the replacement, links supersession, and emits identifiers-only intents. Forget deletes canonical content before any asynchronous projection operation; stale projection events converge to absence. The deletion receipt prevents same-account replay while the account exists and is exported as non-content audit metadata. Account deletion cascades the receipt; replay-after-account-deletion is fenced by the missing owner and missing canonical source, not by orphaned linkable PII.

Extend `KnowledgeUserDataLifecycleParticipant` and the lifecycle integration test in this iteration for usage, conflicts, and deletion receipts. Assert that Claim forget retains only the live-account receipt, while account deletion removes the receipt.

Add counters for confirm, dispute, correct, forget, and Claim usage, using stable action/purpose labels only.

### Verify and commit

```powershell
mvn -Dtest='*KnowledgeClaimControllerTest,ClaimConflictControllerTest,*KnowledgeClaimServiceTest' test
mvn -Dit.test=KnowledgeClaimPersistenceIntegrationTest,UserDataLifecycleServiceIntegrationTest verify -Pintegration
mvn test -Parchitecture
```

Commit: `feat: expose provenance through memory inspector`

## Iteration 2.3 — Purpose-scoped deterministic context assembly

### RED

Add:

- `knowledge/application/service/UserContextAssemblerTest.java`
- `knowledge/application/service/ContextRankingTest.java`
- `knowledge/UserContextIntegrationTest.java`

Cases:

- purpose changes the allowed slices;
- canonical goals/experiment/verified constraints come from deterministic ports, not vectors;
- disputed/refuted/expired claims are labeled or excluded by explicit policy;
- missingness, freshness, trust, and source remain visible;
- duplicate source/hash and self-reinforcing AI chains collapse to one evidence item;
- token/narrative budget truncation is stable and deterministic;
- provider outage has no effect on canonical context.

### GREEN

Add:

- `ContextPurpose { EXPERIMENT_DRAFT, EXPERIMENT_EVALUATION, TELEGRAM_ANSWER }`;
- `UserContextRequest` with period, optional goal/experiment, and narrative limit;
- purpose-specific slice records rather than one public god DTO;
- `UserContextQuery` and `UserContextAssembler`;
- contributor ports for Goal/Experiment, verified constraints, deterministic observations, claims, prior Evaluations, and optional narratives;
- `ContextPolicy`, `ContextCoverage`, `ContextFreshness`, and trust-aware ranking.

Place the experiment read adapter in `knowledge/adapter/out/experiment/`; it imports only the `experiment::query-api` named interface. Expose `UserContextQuery` through `knowledge::context-api` for AI callers and keep `experiment` free of knowledge imports. Add `KnowledgeModuleArchitectureTest` asserting the allowed `knowledge -> experiment::query-api` edge and forbidding reverse `experiment -> knowledge` plus `knowledge -> ai|memory` edges.

The assembler runs canonical SQL reads first. Semantic retrieval is an optional last step only for permitted narratives and past patterns. Its absence returns a valid bundle with a projection-availability flag.

Record Claim usage only after the consuming Decision/answer is durably committed. The usage record includes purpose and consumer ID, not prompt text.

### Verify and commit

```powershell
mvn -Dtest=UserContextAssemblerTest,ContextRankingTest test
mvn -Dit.test=UserContextIntegrationTest verify -Pintegration
mvn test -Parchitecture
```

Commit: `feat: assemble purpose scoped personal context`

## Iteration 2.4 — Versioned vector projection and atomic rebuild

Use the repository `fitness-ai-prompts`, `fitness-security-fix`, and `fitness-testing` skills because embeddings cross the AI egress boundary.

### RED

Add PostgreSQL/provider-mocked tests for:

- source truth saved even when projection is denied or provider is down;
- egress denial causes zero embedding calls;
- stale event cannot overwrite a newer source version;
- forgotten/superseded Claim is absent after replay;
- duplicate event is idempotent by stable vector ID;
- rebuild creates the same active source/version/hash set;
- rebuild failure leaves the previous generation active;
- account deletion during rebuild cannot publish a new owned projection;
- two concurrent rebuilds cannot both activate.

### GREEN

Add `src/main/resources/db/migration/V39__version_memory_projection.sql` with:

- `memory_projection_generations` (`BUILDING`, `ACTIVE`, `FAILED`, owner, schema version, timestamps);
- indexed/generated metadata required to identify source type/id/version/hash/generation in `user_memory`;
- owner and generation FKs with cascade;
- one active generation per owner/projection kind;
- cleanup of legacy projections that treat AI Insight or narrative `GOAL` note as a verified fact.

Add:

- `MemoryProjectionPort` in `knowledge`;
- adapter in `memory` around `VectorStore`;
- `KnowledgeProjectionService`;
- `MemoryProjectionRebuildUseCase` and `MemoryProjectionRebuildService`;
- identifiers-only `KnowledgeClaimChangedListener` that rereads exact current source version;
- rebuild job executor using the fenced durable-job API.

Rebuild protocol:

1. Claim a per-user rebuild job and create `BUILDING` generation.
2. Read canonical allowed claims/narratives in stable ID order.
3. Classify egress before any embedding request.
4. Write deterministic vector IDs containing owner, source, version, schema, and generation.
5. In one transaction validate owner/current generation and atomically activate the new generation.
6. Delete old projection rows only after activation.
7. On denial/failure mark the build failed and preserve the prior active generation.

No raw Claim text is placed in events or logs.

Extend `KnowledgeUserDataLifecycleParticipant` to export generation metadata without embeddings and delete/cancel generations. Extend `MemoryUserDataLifecycleParticipant` to delete the corresponding vectors. Add both participants to `UserDataLifecycleServiceIntegrationTest` in this iteration; no projection table is left for a later lifecycle pass.

### Verify and commit

```powershell
mvn -Dtest='*KnowledgeProjection*Test,*MemoryProjectionRebuild*Test,*AiEgress*Test' test
mvn -Dit.test='KnowledgeProjectionIntegrationTest,MemoryProjectionRebuildIntegrationTest,UserDataLifecycleServiceIntegrationTest' verify -Pintegration
mvn test -Parchitecture
```

Commit: `feat: rebuild versioned memory projections atomically`

## Iteration 2.5 — Contradiction and freshness/drift detection

### RED

Add tests for:

- exact normalized subject/predicate plus overlapping validity and unequal values creates one open conflict;
- equal normalized values do not conflict;
- non-overlapping validity does not conflict;
- superseded/refuted claims stop participating;
- stale source/expired validity creates deterministic drift reason codes;
- user acknowledgement/dismissal is versioned and idempotent;
- no content is silently overwritten or trust-promoted.

### GREEN

Add:

- `ClaimConflict`, `ClaimConflictStatus`, `ConflictReason`;
- `ClaimConflictDetector` pure component;
- `ClaimDriftDetector` pure component;
- `ClaimConflictService` and scheduled/intent-driven refresh;
- Memory Inspector commands for acknowledge/dismiss without altering the Claims.

Behavioral drift inferred from habits, wearables, or probabilistic models remains out of scope. This iteration handles explicit value contradiction, validity expiry, source staleness, and supersession only.

Add counters for surfaced conflicts and drift reasons with enum-only labels. Do not count Claim values or identify users.

### Verify and commit

```powershell
mvn -Dtest='*ClaimConflict*Test,*ClaimDrift*Test' test
mvn -Dit.test=KnowledgeClaimPersistenceIntegrationTest verify -Pintegration
mvn test -Parchitecture
```

Commit: `feat: surface claim contradictions and drift`

## Iteration 2.6 — Experiment/AI integration and Phase 2 gate

### RED

Add end-to-end integration tests proving:

- completed Experiment Evaluation creates a `PROPOSED` or `SUPPORTED` experiment-result Claim only according to deterministic rules;
- AI draft creates at most `AI_HYPOTHESIS/PROPOSED`;
- user Decision records the exact Claims used;
- a disputed/high-risk conflict forces abstention or explicit warning;
- AI-disabled Experiment evaluation and Claim inspection still work;
- export/delete includes all knowledge tables, generations, and projection work;
- replay after deletion cannot resurrect Claim or vector content.

### GREEN

Add narrow cross-module adapters/events:

- `experiment::api` named-interface `ExperimentEvaluationCompletedEvent` with evidence identifiers and deterministic result metadata;
- knowledge listener creating an experiment-result Claim candidate idempotently;
- AI context adapter consuming `UserContextQuery` rather than reading memory/source modules independently;
- Claim usage commit after durable consumer output;
- final audit of `KnowledgeUserDataLifecycleParticipant` and `MemoryUserDataLifecycleParticipant`; new-table coverage was added in the owning iterations.

Audit epistemic-quality metrics: provenance coverage, AI-hypothesis usage, disputed Claim usage, contradiction exposure, and rebuild convergence. Metrics are aggregate and enum-labeled; the target for decisions using an unconfirmed AI-only Claim is zero.

The dependency direction remains `knowledge -> experiment::api` and `ai -> knowledge::context-api`; Experiment stores no KnowledgeClaim foreign key and never imports knowledge. `knowledge_claim_usage` records the Experiment Decision consumer ID from the knowledge side. Add architecture assertions for these final adapters.

Update `docs/runbooks/knowledge-rebuild.md`, the Stage 2 roadmap status, and architecture diagrams. Do not add agent runtime or write capabilities.

### Phase gate and commit

```powershell
mvn test
mvn verify -Pintegration
mvn test -Parchitecture
mvn dependency:analyze
powershell -File .codex/hooks/privacy-scan.ps1
npx graphify hook-rebuild
git diff --check
```

Commit: `feat: integrate trustworthy context boundaries`
