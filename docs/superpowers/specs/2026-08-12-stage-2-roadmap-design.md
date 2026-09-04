# FitnessApp Stage 2 — canonical roadmap and design decisions

**Status:** Phase 0, Phase 1 engineering and Phase 2 engineering complete (2026-09-04); product validation OPEN
**Date:** 2026-08-12
**Scope:** Phase 0 Truth and Recovery, Phase 1 Debugger Alpha, Phase 2 Trustworthy Personal Context

## 1. Authority and document order

Implementation decisions are resolved in this order:

1. `docs/PROJECT_VISION.md` — product purpose, safety principles, ownership, and long-term direction.
2. This document — the canonical execution order and cross-phase boundaries for Stage 2.
3. [`2026-08-09-fitnessapp-product-evolution-review.md`](2026-08-09-fitnessapp-product-evolution-review.md) — decision catalogue and acceptance criteria.
4. [`Phase 0 implementation plan`](../plans/2026-08-12-phase-0-truth-recovery.md), [`Phase 1 implementation plan`](../plans/2026-08-12-phase-1-debugger-alpha.md), and [`Phase 2 implementation plan`](../plans/2026-08-12-phase-2-trustworthy-context.md).
5. `TESTING.md`, ADRs, and operational runbooks.
6. `STABLE_BASE_BACKLOG.md` and `BACKLOG.md` — historical completion ledgers, not the Stage 2 roadmap.

The Stable Base backlog remains truthfully complete for its original scope. Stage 2 Phase 0 adds stronger crash, lease, migration, and provider-policy guarantees that were not part of that scope.

## 2. Product decision

Stage 2 builds a deterministic personal progress debugger, not a general autonomous agent.

The first complete user loop is:

```text
problem Investigation
  -> structured Goal
  -> testable Hypothesis
  -> one controlled Experiment
  -> adherence + outcome evidence
  -> deterministic Evaluation
  -> explicit user Decision
  -> trustworthy reusable context
```

The product must complete this loop with all AI providers disabled. AI may draft an explanation or proposal only after the deterministic workflow and its evidence already exist.

## 3. Module ownership

The modular monolith remains the deployment and transaction boundary.

| Module | Owns | Does not own |
|---|---|---|
| `experiment` | Investigation, Goal, Hypothesis, Experiment, CheckIn, Outcome, Evaluation, Decision, cycle metrics | model prompts, provider calls, raw nutrition/workout data |
| `knowledge` | canonical KnowledgeClaim, provenance, trust, supersession, contradiction/drift state, claim usage | embeddings, raw source records, product lifecycles |
| `memory` | rebuildable pgvector semantic projection | canonical claims or source truth |
| `ai` | egress policy, routing, prompts, model execution, validation of model output | Investigation/Goal/Experiment/Claim lifecycle |
| `nutrition` / `workout` | their raw records and source-specific provenance | experiment decisions and knowledge claims |
| `telegram` | channel normalization, authentication/linking, delivery outbox | product state |
| `auth` | current user and lifecycle orchestration | SQL belonging to business modules |
| `api` | narrow neutral cross-module contracts and events | controllers, repositories, provider types |

Every new module implements `UserDataLifecycleParticipant`. `auth` orchestrates export and erasure without knowing the module's tables.

## 4. Cross-cutting invariants

1. Every personal row has an enforced owner or is intentionally anonymous and short-lived.
2. Controllers derive the user from `CurrentUserApi`; a caller cannot choose another `userId`.
3. Domain transition and durable intent commit in one PostgreSQL transaction.
4. External I/O occurs after commit and outside a database transaction.
5. A worker mutation is accepted only for the current lease owner and generation.
6. Exhausted work reaches an explicit terminal or uncertainty state; it never remains unclaimable `PENDING`.
7. PostgreSQL is canonical. Embeddings, summaries, and caches are projections that can be rebuilt.
8. Retrieved text is untrusted data. It cannot grant a capability, alter policy, or silently become a verified fact.
9. AI never owns arithmetic, permissions, domain lifecycles, retention, or final evaluation.
10. Existing Flyway migrations are immutable. Historical repairs use versioned, idempotent upgrade-bridge scripts and append-only migrations.
11. Logs, metrics, and error messages use IDs and stable codes, never raw notes, prompts, Telegram text, nutrition detail, tokens, or claim values.
12. Each implementation iteration is TDD-first, independently reviewed, verified with the relevant gates, and committed separately.

### Module dependency DAG

The intended compile-time arrows are one-way:

```text
telegram  -> experiment::command-api
ai        -> experiment::draft-spi
ai        -> knowledge::context-api
knowledge -> experiment::query-api
knowledge -> experiment::api events
memory    -> knowledge::projection-spi
nutrition -> api::evidence-source
workout   -> api::evidence-source
experiment -> api::evidence-source
knowledge -> api::lifecycle
knowledge -> auth::current-user (Memory Inspector authentication)
knowledge -> job (fenced projection rebuild)
ai        -> api::delivery (owned durable answer queue)

experiment -> api only for truly neutral shared contracts
knowledge  -> api only for truly neutral shared contracts
```

There is no `experiment -> knowledge`, `experiment -> ai`, `knowledge -> ai`, or `knowledge -> memory` implementation dependency. The consuming module owns the adapter: knowledge calls an exposed experiment query through a knowledge-side adapter; AI implements the exposed experiment draft SPI; memory implements the exposed knowledge projection SPI. Domain-specific identifiers-only events live in the owning module's exposed `api` named interface; the top-level `api` module is reserved for genuinely neutral contracts. The evidence contracts live in `api/evidence/` with `@NamedInterface("evidence-source")`; consumers depend on `api::evidence-source`, not broad `api`. `package-info.java` files declare `@ApplicationModule(allowedDependencies=...)` and `@NamedInterface` surfaces; architecture tests assert both allowed arrows and forbidden reverse edges before integration code is added.

### Implemented transaction flow

```text
Experiment Evaluation + evidence snapshot --commit--> identifiers-only event
  -> knowledge rereads owned Evaluation -> idempotent result Claim

Canonical purpose-scoped context -> optional retrieval -> canonical refresh -> AI call (no DB transaction)
  -> validated draft: PROPOSED hypothesis only, never an Experiment transition
  -> Telegram answer: owner/current Claim check + owned outbox + exact usage --commit
  -> periodic report: owner/current Claim check + report + exact usage + event --commit

Explicit user Decision -> experiment-owned API seam
  -> knowledge checks exact references -> Decision + exact usage + receipt --commit

Canonical Claims -> guarded embeddings -> BUILDING vectors
  -> current owner/lease/source manifest/base generation -> atomic ACTIVE swap
```

Report/Decision guards abstain on unacceptable context; interactive answers can carry explicit
server warnings for unconfirmed non-constraint narratives. Claim usage means durable output,
not vector retrieval or confirmed Telegram delivery. The current recovery contract is
[knowledge-rebuild.md](../../runbooks/knowledge-rebuild.md).

## 5. Alternatives considered

### Product domain in `ai`

Rejected. Provider replacement would then alter business state, and the product would not work without a model.

### One generic `personal-os` or `context` god module

Rejected. It would combine source ownership, experiments, claims, retrieval, and channels. `experiment` and `knowledge` are separate bounded contexts with narrow public contracts.

### Full knowledge graph or separate vector database in Phase 2

Rejected. PostgreSQL relations and typed claims are sufficient for the first trustworthy-context release. pgvector remains a rebuildable search projection.

### Event sourcing rewrite

Rejected. The required guarantees come from canonical state, versioned domain events, durable intent, idempotent consumers, and tested recovery.

### Editing historical Flyway migrations

Rejected. A database that has recorded a checksum must remain upgradeable. Precondition repair happens before the blocking historical version through documented bridge scripts; current-state normalization uses new migrations.

### Unfenced timeout recovery

Rejected. Time alone cannot identify the current claimant. Both durable jobs and Telegram delivery require owner plus monotonically increasing lease generation.

### AI-first alpha

Rejected. Manual deterministic operation is the default and the acceptance baseline. Optional AI drafts remain disabled until grounding, egress, and no-provider tests pass.

## 6. Canonical execution order

### Iteration 0.1 — Stage 2 fixation

Deliver this design, the three phase plans, external-policy research conclusions, and a reviewed ordered backlog. No production code changes.

Gate: documentation consistency review, link/path verification, `git diff --check`.

### Phase 0 — Truth and Recovery

Execute in this order:

1. Dirty-upgrade fixture harness and pre-version bridge policy.
2. Versioned module-owned personal-data export.
3. Durable-job lease fencing and terminal recovery.
4. Telegram outbox lease fencing, just-in-time claiming, and delivery uncertainty.
5. Atomic domain-state plus durable-intent publication.
6. Conservative FatSecret storage/provenance/non-retention policy.
7. Current dirty-state normalization plus the full historical upgrade matrix.
8. Spring 3.5 compatibility checkpoint.
9. Supported Boot 4.1 platform baseline and honest dependency analysis.
10. Full Phase 0 gates and operational documentation.

Phase 1 cannot start until all Phase 0 exit criteria are proven.

### Phase 1 — Debugger Alpha

Execute in this order:

1. `experiment` module skeleton, Investigation, canonical Goal, ownership, lifecycle, REST API.
2. Experiment aggregate and guarded state machine.
3. Check-ins, adherence, outcomes, and deterministic Evaluation.
4. Evidence references, `AlphaExperimentContext`, data-sufficiency rules, Recommendation/Decision records.
5. Telegram workflow over the same application use cases.
6. Optional grounded AI draft behind a default-off feature flag.
7. Product instrumentation, complete lifecycle/export audit, and the Alpha engineering-readiness gate.

Phase 1 has two distinct gates:

- **Alpha engineering-ready:** all deterministic workflow, safety, lifecycle, migration, observability, and Maven gates pass. This authorizes controlled dogfood and Phase 2 engineering.
- **Alpha product-validated:** the evidence ledger records dogfood plus 3–5 target users, completed evidence-bearing 7–14 day cycles, time-to-value, evaluation completion, and second-cycle starts. Automated tests cannot close this gate.

### Phase 2 — Trustworthy Personal Context

Execute in this order:

1. `knowledge` module and canonical typed KnowledgeClaim.
2. Provenance, trust, supersession, correction, dispute, and forget workflows.
3. Use-case-specific context assembly and claim-usage audit.
4. Policy-guarded vector projection and full rebuild.
5. Deterministic contradiction and freshness/drift detection.
6. Memory Inspector API, integration with Experiment/AI, lifecycle, and Phase 2 exit gate.

## 7. Phase exit gates

### Phase 0

- FatSecret policy is enforced in schema, services, export, disconnect, deletion, replay, and integration tests.
- A stale durable-job or Telegram claimant cannot mutate a newer claim.
- Durable last-attempt crashes reach terminal failure; every expired in-flight Telegram send with unknowable provider acceptance reaches `DELIVERY_UNKNOWN` and is never automatically resent.
- Domain data and durable intent survive the injected crash window.
- Supported upgrade paths include invalid historical rows, timestamps, publications, jobs/outbox, malformed memory metadata, and transactional migration rollback.
- The selected Java 21/Spring baseline is officially supportable and all dependency declarations are honest.
- `mvn test`, `mvn verify -Pintegration`, and `mvn test -Parchitecture` pass.

### Phase 1 engineering-ready

- A user can complete the full manual loop through REST and Telegram.
- Exactly one in-flight Experiment per user is enforced in PostgreSQL.
- Evaluation cannot be created without sufficient outcome and adherence evidence.
- `KEEP | MODIFY | DROP | INCONCLUSIVE` is computed deterministically; the user owns the final Decision.
- Readiness, sleep, and mood are optional check-in context, not diagnosis or general trackers.
- AI-disabled tests prove the complete loop; optional AI remains default-off and grounded.
- Account export and deletion cover every Phase 1 row and pending work.
- Product counters measure started cycles, evaluated cycles, time-to-evaluation, and second-cycle starts without PII metric labels.
- The engineering-ready gate is recorded separately from the still-open product-validation evidence gate.

### Phase 1 product-validated

- The owner dogfood cycle reaches Evaluation and records all out-of-app work.
- 3–5 external target users are observed using the workflow.
- Five target users start an Experiment, at least three reach Evaluation, and at least two voluntarily start a second cycle.
- Evidence shows useful intermediate value within seven days and the 7–14 day cycle is workable.

This gate requires observed user evidence and cannot be closed by tests or synthetic fixtures. Phase 2 engineering depends on `Phase 1 engineering-ready`, not on prematurely claiming this market-validation gate.

### Phase 2

- Every Claim has visible source, time, trust, status, and supersession history.
- A user can confirm, correct, dispute, or forget a Claim.
- AI output never becomes a verified fact automatically.
- Context assembly is deterministic for canonical facts and reports missingness/freshness/trust.
- Contradictions are shown, not silently resolved.
- The vector store can be deleted and fully rebuilt from PostgreSQL truth.
- Rebuild, replay, deletion, and egress-denial tests prove no cross-user or stale projection resurrection.

## 8. Commit and review protocol

For every numbered implementation iteration:

1. Add or change the smallest test that fails for the missing behavior.
2. Run the narrow red test and preserve its evidence.
3. Implement the minimum coherent vertical slice.
4. Run narrow green tests, then the phase-relevant Maven gate.
5. Run `npx graphify hook-rebuild` when code changed and the command is available.
6. Run the privacy scan/hook and `git diff --check`.
7. Request an independent read-only review using the repository review prompt.
8. Resolve every Critical/Important finding and rerun affected tests.
9. Commit only that iteration using a conventional message.

No phase is declared complete from static inspection alone.

## 9. Explicit non-goals for Stage 2

- autonomous write agents, Koog, or a general tool-calling runtime;
- wearables, general sleep/mood/work modules, diagnosis, or medical advice;
- a universal Timeline UI;
- microservices, Kafka, Neo4j, RDF/OWL, or a second vector database;
- automatic conflict resolution or automatic promotion of AI claims;
- monetization and broad consumer UX redesign.

## 10. Terminology reconciliation

The product-review item `PRD-001` used `Topic` for a debugger problem workspace. The owner's note and `PROJECT_VISION.md` use Topics for broader living research areas in the future personal knowledge model. Stage 2 therefore names the bounded debugger workspace `Investigation` and reserves `Topic` for the later research/knowledge concept. This is a naming correction, not removal of the `PRD-001` behavior.

## 11. Engineering completion audit — 2026-09-04

This preserves the complete approved scope, rather than treating only the final AI changes as Stage 2.
Current production revision: `e9709e4`; the closing commit changes documentation only. Full current
verification is recorded in the [Phase 2 plan](../plans/2026-08-12-phase-2-trustworthy-context.md).
The rows below map requirements to implemented artifacts and executable evidence, not just commit titles.

| Iteration | Implemented boundary / artifact | Current verification evidence |
|---|---|---|
| 0.1 | This roadmap, three phase plans, ADR-0015 policy/platform decisions | Committed authority/order and policy references |
| 0.2 | Four `db/upgrade/pre-v*` bridges, ADR-0004, historical operations checkpoints | `HistoricalUpgradeIntegrationTest`: dirty keys/DST/invariants/owner metadata/publications, repeat bridges, DDL+DML rollback |
| 0.3 | Module lifecycle SPI, export manifest, `/api/v2/user/me/export`; V1 retained | Manifest/controller tests and `UserDataLifecycleServiceIntegrationTest`: order, disclosures, owner isolation, no secrets |
| 0.4 | V29, claimed DTO, heartbeat, owner/generation guarded job outcomes and terminal recovery | `DurableJobLeaseConcurrencyIntegrationTest`, worker/service/scheduler tests |
| 0.5 | V30, just-in-time outbox claim, `DELIVERY_UNKNOWN`, unlink/delete fencing | Outbox concurrency/retention tests, mocked provider and timeout classification |
| 0.6 | V31 source versions/lifecycle epochs, short commit services, identifiers-only durable events, ADR-0016 | Nutrition/workout atomic-publication, domain-replay and daily-source-concurrency tests |
| 0.7 | V32 identifier whitelist/connection epoch; no durable restricted FatSecret content; ADR-0015 | `FatSecretRetentionIntegrationTest`, provider unit tests and lifecycle/disconnect replay tests; no optional persistent cache |
| 0.8 | Dirty current-state normalization and bridge matrix through latest schema | Historical-upgrade and durable-ownership migration suites |
| 0.9–0.10 | Bridge commit `21ab663`, target `eaa9fd0`; Boot 4.1 / AI 2.0 / Modulith 2.1, V33, Jackson 3 | Current effective POM/tree and dependency analysis; smoke/replay/upgrade suites; no mixed Boot 3 runtime |
| 0.11 | `bff400d`, TESTING, operations, Stable Base separation and decision register | Phase 0 remains covered by the full current regression gate |
| 1.1 | V34, Investigation/Goal/receipts, owned REST, primary-active Goal uniqueness, lifecycle | Domain/controller/ownership tests and module architecture |
| 1.2 | V35, Experiment state machine, one intervention and one in-flight slot, versioned transitions | Experiment domain/service/controller and concurrency tests |
| 1.3 | V36 check-ins/outcomes/evaluations/decisions/evidence; deterministic insufficiency/adherence/confounders | Evaluation/evidence/controller tests; primary outcome required; missing check-in stays UNKNOWN |
| 1.4 | Neutral evidence API, version/hash identity, deterministic Alpha context | `AlphaExperimentContextIntegrationTest`, context/evidence unit tests; no AI dependency |
| 1.5 | Normalized command kernel and owned `/goal`, `/experiment`, `/checkin`, `/outcome`, `/evaluate` workflow | Product handler/kernel/private-link/replay tests and outbox concurrency |
| 1.6 | Default-off draft SPI, egress/grounding/output validator, versioned proposal templates | Draft/egress offline tests; invalid evidence, unsafe/multiple interventions and ungrounded output rejected |
| 1.7 | `49db314`, enum-only cycle metrics, complete lifecycle and debugger dogfood runbook | AI-disabled `DebuggerAlphaWorkflowIntegrationTest`, metrics and lifecycle tests; product ledger stays OPEN |
| 2.1 | V37 typed Claims/evidence/receipts, provenance and separate trust/status axes | Claim/value/service/persistence tests: canonical hashes, ownership, monotonic replay and supersession |
| 2.2 | V38 Memory Inspector, correction lineage, confirm/dispute/forget, usage and hashed deletion fences | Inspector web/persistence tests: current-user scope, conflicts, history, replay after forget/delete |
| 2.3 | Purpose-specific UserContext, deterministic SQL, optional narrative search, dedup/trust/budgets | Ranking/assembler/PostgreSQL tests: source refresh, missingness, stale/foreign IDs and provider outage |
| 2.4 | V39 generation schema/DB fences, guarded projection/rebuild, lifecycle coverage | Projection/rebuild/upgrade tests: exact manifest, failure preservation, concurrent activation and owner deletion |
| 2.5 | V40 contradiction/drift and versioned notification actions, no automatic truth changes | Detector, consistency concurrency, MockMvc and historical-upgrade tests |
| 2.6 | Evaluation Claims, exact Decision/AI usage (V41), PROPOSED drafts, quality metrics, lifecycle/rebuild docs and diagrams | AI-disabled workflow, UserContext and external-I/O PostgreSQL suites; complete Maven/architecture/dependency/privacy gate |

Final lifecycle review matched V37–V41 tables against the knowledge participant's nine export/erasure
categories; generation/vector metadata excludes embeddings and lease details. Jobs and Modulith
publications remain separately module-owned and owner-cascaded. V34–V36 tables retain their complete
Experiment export/deletion coverage. Existing migrations were not edited during Phase 2.

Review was performed inline under the owner's no-subagent instruction; it is not represented as an
independent second-agent review. No new tests were added by this final documentation audit. Required
existing regression suites were run in full. Local Graphify artifacts are excluded from commits.

Engineering completion does **not** assert product validation, deployment, provider-side erasure,
global truth, model-citation completeness, orphan-generation garbage collection or autonomous action.
The [product-validation ledger](../../validation/debugger-alpha-product-gate.md) requires real observed
cycles/users and stays OPEN. Those observations and Phase 3+ are not silently marked implemented.
