# Phase 1 Debugger Alpha — implementation plan

> Execute only after the Phase 0 exit gate in the [`Stage 2 canonical roadmap`](../specs/2026-08-12-stage-2-roadmap-design.md) is green. Each numbered iteration ends with independent review and its own commit.

**Goal:** deliver a fully manual, deterministic, evidence-bearing progress-debugging loop through REST and Telegram. AI is an optional default-off draft adapter added last.

## Domain contract

`Investigation` is the bounded workspace for one concrete performance problem. It fulfills the behavior described as `Topic` in `PRD-001` without consuming the future knowledge/research meaning of Topic.

State machines:

```text
Investigation: OPEN -> COLLECTING_BASELINE -> READY_FOR_EXPERIMENT
             -> EXPERIMENTING -> RESOLVED | ARCHIVED

Goal: DRAFT -> ACTIVE -> PAUSED -> ACHIEVED | ABANDONED | SUPERSEDED

Experiment: DRAFT -> PROPOSED -> ACCEPTED | REJECTED
            ACCEPTED -> ACTIVE | ABORTED
            ACTIVE -> PAUSED | COMPLETED | ABORTED
            PAUSED -> ACTIVE | COMPLETED | ABORTED
            COMPLETED -> EVALUATED
```

`ACCEPTED`, `ACTIVE`, and `PAUSED` occupy the user's single in-flight slot. Every mutable aggregate has a numeric version. Commands carry `expectedVersion` and `idempotencyKey`.

Evaluation decisions are exactly `KEEP`, `MODIFY`, `DROP`, and `INCONCLUSIVE`. The backend deterministically calculates effect, coverage, adherence, freshness, and confounder flags. The user confirms the final Decision.

## Iteration 1.1 — Module, Investigation, and canonical Goal

### RED

Add:

- `src/test/java/com/fit/fitnessapp/experiment/domain/InvestigationTest.java`
- `src/test/java/com/fit/fitnessapp/experiment/domain/GoalTest.java`
- `src/test/java/com/fit/fitnessapp/experiment/adapter/out/persistence/ExperimentOwnershipIntegrationTest.java`
- `src/test/java/com/fit/fitnessapp/experiment/adapter/in/web/InvestigationControllerTest.java`
- `src/test/java/com/fit/fitnessapp/experiment/adapter/in/web/GoalControllerTest.java`
- `src/test/java/com/fit/fitnessapp/experiment/ExperimentModuleArchitectureTest.java`

Prove legal/illegal transitions, optimistic version rejection, current-user isolation, cascade deletion, non-owner lookup returning not found, and concurrent primary-goal activation. For every Investigation/Goal route, MockMvc covers unauthenticated access, request validation bounds, stable error codes, server-derived Current User, cross-user not-found behavior, optimistic conflicts, and duplicate-command idempotency.

Run:

```powershell
mvn -Dtest=InvestigationTest,GoalTest,InvestigationControllerTest,GoalControllerTest test
mvn -Dit.test=ExperimentOwnershipIntegrationTest verify -Pintegration
```

### GREEN

Add `src/main/resources/db/migration/V34__create_experiment_core.sql` with:

- `investigations`;
- `goals`;
- owner FK `users(id) ON DELETE CASCADE` on both tables;
- `TIMESTAMPTZ` timestamps;
- status and value constraints;
- `(user_id, id)` supporting uniqueness for composite ownership FKs;
- partial unique index for one primary `ACTIVE` Goal per user;
- numeric `aggregate_version`;
- `experiment_command_receipts` with owner FK `users(id) ON DELETE CASCADE`, aggregate type/id, idempotency key, result version, created time, and owner-scoped uniqueness.

Add module structure:

- `experiment/package-info.java`
- `experiment/api/package-info.java` exposed as `experiment::api` for identifiers-only owned events;
- `experiment/application/port/in/package-info.java` exposing separate `command-api` and `query-api` named interfaces;
- `experiment/spi/package-info.java` exposed as `draft-spi` without an implementation dependency on AI;
- `experiment/domain/Investigation.java`
- `experiment/domain/InvestigationStatus.java`
- `experiment/domain/Goal.java`
- `experiment/domain/GoalStatus.java`
- `experiment/domain/GoalType.java`
- `experiment/domain/GoalMetric.java`
- `experiment/domain/TargetRange.java`
- `experiment/domain/GoalSource.java`
- `experiment/application/port/in/InvestigationCommandUseCase.java`
- `experiment/application/port/in/InvestigationQueryUseCase.java`
- `experiment/application/port/in/GoalCommandUseCase.java`
- `experiment/application/port/in/GoalQueryUseCase.java`
- `experiment/application/port/out/InvestigationRepositoryPort.java`
- `experiment/application/port/out/GoalRepositoryPort.java`
- `experiment/application/service/InvestigationService.java`
- `experiment/application/service/GoalService.java`
- JDBC persistence adapters under `experiment/adapter/out/persistence/`;
- request/response records and controllers under `experiment/adapter/in/web/`.

Routes:

```text
POST   /api/v1/investigations
GET    /api/v1/investigations
GET    /api/v1/investigations/{id}
POST   /api/v1/investigations/{id}/transitions
POST   /api/v1/goals
GET    /api/v1/goals
GET    /api/v1/goals/{id}
POST   /api/v1/goals/{id}/transitions
```

Controllers obtain `userId` only from `CurrentUserApi`. Transition requests contain a typed command, `expectedVersion`, `idempotencyKey`, and optional reason.

Declare `@ApplicationModule(allowedDependencies={"auth", "api::lifecycle"})` for the initial module, using the existing lifecycle SPI through an explicitly named neutral interface. Before Iteration 1.4, extend it only with `api::evidence-source`. Architecture tests forbid `experiment -> ai|knowledge|memory|telegram` and forbid imports from unlisted `api` packages; consumers import named interfaces, never experiment adapters.

Add `ExperimentUserDataLifecycleParticipant` immediately. It exports Investigation, Goal, and non-content command-receipt records and deletes them by owner. Add these collections to the versioned export manifest rather than extending the legacy hard-coded V1 DTO. Extend V2 export tests and `UserDataLifecycleServiceIntegrationTest` for all three V34 tables before committing.

Publish identifiers-only `InvestigationCreated` and `GoalActivated` product events and add counters with stable status/type labels only. Tests assert no username, email, free text, or user ID metric tag.

### Verify and commit

```powershell
mvn -Dtest=InvestigationTest,GoalTest,InvestigationControllerTest,GoalControllerTest test
mvn -Dit.test=ExperimentOwnershipIntegrationTest,UserDataLifecycleServiceIntegrationTest verify -Pintegration
mvn test -Parchitecture
git diff --check
```

Commit: `feat: add investigations and canonical goals`

## Iteration 1.2 — Experiment aggregate and concurrency invariants

### RED

Add:

- `experiment/domain/ExperimentTest.java`
- `experiment/application/service/ExperimentServiceTest.java`
- `experiment/adapter/out/persistence/ExperimentConcurrencyIntegrationTest.java`
- `experiment/adapter/in/web/ExperimentControllerTest.java`

Prove the full state graph, exactly one intervention, required baseline/duration/primary metric/stop conditions, versioned transition idempotency, and concurrent `ACCEPT` where exactly one experiment wins the single in-flight slot. MockMvc cases cover unauthenticated access, current-user scoping, invalid input, stable error codes, optimistic conflicts, and duplicate command idempotency.

### GREEN

Add `src/main/resources/db/migration/V35__create_experiment_lifecycle.sql` with:

- `experiments`;
- `experiment_transitions`;
- composite owner FKs to Investigation and Goal;
- partial unique index on `experiments(user_id)` where status is `ACCEPTED`, `ACTIVE`, or `PAUSED`;
- checks for baseline windows, duration, exactly one primary intervention, and terminal timestamps.

Add:

- `Experiment.java`, `ExperimentStatus.java`, `Hypothesis.java`, `Intervention.java`, `StopCondition.java`;
- `ExperimentTransition.java`, `ExperimentCommand.java`, `ExperimentCommandReceipt.java`;
- `ExperimentCommandUseCase`, `ExperimentQueryUseCase`, `ExperimentRepositoryPort`;
- `ExperimentService` and JDBC persistence adapter;
- `ExperimentController` and stable error mapping for invalid transition, version conflict, and in-flight conflict.

Every state change locks or conditionally updates the aggregate with `WHERE user_id=? AND id=? AND aggregate_version=?`, appends one transition audit row, increments the version, and records the idempotency receipt in one transaction. Experiment transitions reuse the generic `experiment_command_receipts` table created solely by V34; V35 does not recreate or alter its ownership contract.

Add `ExperimentChangedEvent` carrying only versioned metadata and identifiers, no hypothesis/intervention text.

Add `experiment.started` and `experiment.second_cycle_started` counters in this owning iteration. Second-cycle detection is deterministic from prior non-draft Experiments and does not require knowledge claims.

Extend `ExperimentUserDataLifecycleParticipant` in this same iteration to export/delete Experiments, transitions, and command receipts. Extend `UserDataLifecycleServiceIntegrationTest` before committing so an intermediate revision never has undisclosed personal tables.

### Verify and commit

```powershell
mvn -Dtest=ExperimentTest,ExperimentServiceTest,ExperimentControllerTest test
mvn -Dit.test=ExperimentConcurrencyIntegrationTest,UserDataLifecycleServiceIntegrationTest verify -Pintegration
mvn test -Parchitecture
```

Commit: `feat: add fenced experiment lifecycle`

## Iteration 1.3 — Check-ins, adherence, Outcome, and Evaluation

### RED

Add:

- `experiment/domain/ExperimentCheckInTest.java`
- `experiment/domain/EvaluationTest.java`
- `experiment/application/service/ExperimentEvaluationServiceTest.java`
- `experiment/adapter/out/persistence/ExperimentEvidenceIntegrationTest.java`
- controller tests for check-in, outcome, evaluation, and decision endpoints.

Required cases:

- a missing check-in is `UNKNOWN`, never `NO`;
- adherence supports `YES`, `NO`, `PARTIAL`, `UNKNOWN`;
- readiness, sleep, and mood are nullable bounded context fields;
- duplicate check-in date and duplicate outcome command converge;
- completion without the primary outcome remains incomplete;
- insufficient samples, stale data, low adherence, or unresolved confounders yield `INCONCLUSIVE` or a stable insufficiency code;
- non-adherence cannot be presented as evidence that the intervention failed;
- only a completed experiment can be evaluated and evaluation is single-versioned.
- every endpoint rejects unauthenticated and cross-user access, validates payload bounds, returns stable version/idempotency errors, and never trusts a request `userId`.

### GREEN

Add `src/main/resources/db/migration/V36__create_experiment_evidence.sql`. Create:

- `experiment_check_ins`;
- `experiment_outcomes`;
- `experiment_evaluations`;
- `experiment_decisions`;
- `experiment_evidence_refs`;
- unique `(experiment_id, local_date)` and command-idempotency constraints;
- owner/composite FKs and cascade behavior.

`experiment_evidence_refs` is created here in V36 and exported/deleted in this iteration, even though the context assembler that reads it arrives in 1.4. V36 is complete and immutable after this commit.

Add domain types:

- `AdherenceStatus`, `ExperimentCheckIn`, `ContextRating`, `Outcome`, `Evaluation`;
- `EvaluationDecision`, `DataQuality`, `ObservedEffect`, `ConfounderAssessment`, `UserDecision`.

Add use cases/services/controllers:

- `ExperimentCheckInUseCase` / `ExperimentCheckInService`;
- `ExperimentEvaluationUseCase` / `ExperimentEvaluationService`;
- `POST /api/v1/experiments/{id}/check-ins`;
- `POST /api/v1/experiments/{id}/outcomes`;
- `POST /api/v1/experiments/{id}/evaluation`;
- `POST /api/v1/experiments/{id}/decision`.

The calculator is a pure deterministic component. It returns calculation inputs and reason codes with the result so tests and users can reproduce it.

Add `experiment.evaluated`, decision-distribution, and time-to-evaluation metrics in this owning iteration, with stable enum labels and no personal tags.

Extend `ExperimentUserDataLifecycleParticipant`, V2 export assertions, and deletion integration coverage for check-ins, outcomes, evaluations, decisions, and their receipts in this iteration.

### Verify and commit

```powershell
mvn -Dtest='ExperimentCheckInTest,EvaluationTest,ExperimentEvaluationServiceTest,*Experiment*ControllerTest' test
mvn -Dit.test=ExperimentEvidenceIntegrationTest,UserDataLifecycleServiceIntegrationTest verify -Pintegration
mvn test -Parchitecture
```

Commit: `feat: evaluate experiment evidence deterministically`

## Iteration 1.4 — EvidenceRef and purpose-built alpha context

### RED

Add:

- `experiment/domain/EvidenceRefTest.java`
- `experiment/application/service/AlphaExperimentContextServiceTest.java`
- `experiment/adapter/out/persistence/AlphaExperimentContextIntegrationTest.java`

Prove source-version/hash validation, deterministic selection order, missingness and freshness reporting, per-user isolation, no duplicated evidence, and no AI dependency.

### GREEN

Add:

- `EvidenceRef(sourceType, sourceId, sourceVersion, contentHash, observedAt)`;
- `DataCoverage`, `EvidenceFreshness`, `AlphaExperimentContext`;
- neutral `api/evidence/EvidenceSourceQuery.java`, `EvidenceSourceRequest.java`, and `EvidenceSourceSlice.java` contracts with no experiment domain types;
- `api/evidence/package-info.java` annotated `@NamedInterface("evidence-source")`;
- nutrition and workout implementations of the neutral evidence-source query, plus the experiment-owned check-in contributor;
- `AlphaExperimentContextService`;
- experiment-side aggregation over the injected neutral `EvidenceSourceQuery` implementations.

The context contains only the current Investigation, active Goal, candidate/current Experiment, deterministic source coverage, permitted evidence refs, and missing fields. It does not query vector memory or previous AI prose. `experiment`, nutrition, and workout declare only `api::evidence-source` for this collaboration; nutrition/workout do not import experiment, and experiment does not import their modules or the rest of `api`. Architecture tests assert named-interface use and both forbidden directions.

Extend the module participant and lifecycle integration test for persisted `experiment_evidence_refs` in this same iteration.

### Verify and commit

```powershell
mvn -Dtest=EvidenceRefTest,AlphaExperimentContextServiceTest test
mvn -Dit.test=AlphaExperimentContextIntegrationTest,UserDataLifecycleServiceIntegrationTest verify -Pintegration
mvn test -Parchitecture
```

Commit: `feat: assemble deterministic alpha experiment context`

## Iteration 1.5 — Channel-neutral commands and Telegram workflow

### RED

Add tests for:

- SDK Update normalization to `InboundCommand`;
- private-chat and current-link enforcement before product dispatch;
- `/goal`, `/experiment`, `/checkin`, `/outcome`, `/evaluate` happy and invalid flows;
- duplicate Telegram update idempotency;
- unlink/account deletion between product commit and delivery;
- no raw command text in logs.

### GREEN

Add neutral command records/interfaces in `telegram/application/port/in/`:

- `InboundCommand`;
- `CommandResult`;
- `CommandKernel`.

Refactor `TelegramUpdateHandler` to normalize the SDK update once, enforce private chat/linking, then call the kernel. Existing handlers migrate without behavior changes. Add product handlers that call the same experiment use cases as REST. All private responses use `enqueueOwnedMessage` and the fenced outbox.

Keep multi-step state in `ConversationStateUseCase`; store IDs and typed step data, not duplicated experiment content.

### Verify and commit

```powershell
mvn -Dtest='*CommandHandlerTest,TelegramUpdateHandlerPrivacyLoggingTest' test
mvn -Dit.test=TelegramOutboxConcurrencyIntegrationTest verify -Pintegration
mvn test -Parchitecture
```

Commit: `feat: expose debugger workflow through telegram`

## Iteration 1.6 — Optional grounded AI proposal draft

Use the repository `fitness-ai-prompts`, `fitness-security-fix`, and `fitness-testing` skills for this iteration.

### RED

Add offline tests for:

- feature flag false and provider unavailable: manual flow remains complete;
- insufficient data: zero provider calls;
- egress denial: zero embedding/chat provider calls;
- prompt-injection text treated as untrusted context;
- output with unknown EvidenceRef, multiple interventions, medical diagnosis, unsafe stop conditions, or arithmetic contradiction is rejected;
- accepted output remains only a draft and cannot transition the Experiment.

### GREEN

Add exposed seam:

- `experiment/spi/ExperimentDraftGenerator.java`;
- `ExperimentDraftRequest.java`;
- `ExperimentDraft.java`.

Add AI adapter:

- `ai/adapter/out/experiment/AiExperimentDraftGenerator.java`;
- `src/main/resources/ai/prompts/experiment-proposal-v1.md`;
- `AiExperimentDraftValidator.java`;
- property `app.ai.experiment-draft-enabled=false` in all default configs.

The adapter receives only `AlphaExperimentContext` and tagged untrusted user problem text. It classifies egress before every embedding/model operation. Backend values override model calculations. The returned draft requires explicit user edit/accept through normal Experiment commands.

### Verify and commit

```powershell
mvn -Dtest='*ExperimentDraft*Test,*AiEgress*Test' test
mvn test -Parchitecture
```

Commit: `feat: add optional grounded experiment drafts`

## Iteration 1.7 — Instrumentation and Alpha engineering-readiness gate

### RED

Extend:

- `UserDataLifecycleServiceIntegrationTest` audit for all experiment tables, transitions, receipts, evidence, and pending work already added by their owning iterations;
- export audit for the complete module manifest and no cross-user data;
- `DebuggerAlphaWorkflowIntegrationTest` for the complete AI-disabled cycle;
- metrics tests proving counters have no username/email/userId tags.

### GREEN

Audit `ExperimentUserDataLifecycleParticipant` rather than deferring new-table coverage to this iteration. Audit the already-owned Micrometer counters/timers for:

- experiment started;
- experiment evaluated;
- evaluation decision distribution;
- time from Investigation creation to Evaluation;
- second cycle started.

Labels are stable enums only. Add `docs/runbooks/debugger-alpha.md` with manual dogfood flow and failure recovery. Record `ALPHA_ENGINEERING_READY` only after the automated gate passes; this authorizes Phase 2 engineering and controlled validation, not a product-validation claim.

The separate product-validation ledger is created at `docs/validation/debugger-alpha-product-gate.md` only from observed, privacy-safe aggregate evidence. It closes `ALPHA_PRODUCT_VALIDATED` only when all of these are true:

- one owner dogfood Experiment reaches Evaluation and records every out-of-app action;
- 3–5 external target users are observed using the workflow;
- five target users start an Experiment;
- at least three reach Evaluation after an evidence-bearing 7–14 day cycle;
- at least two start a second cycle without prompting;
- the first seven days show the promised intermediate value and cold-start baseline behavior.

Automated tests, synthetic fixtures, and the agent cannot satisfy these observations. Until the ledger contains real evidence, the product-validation status remains open while Phase 2 engineering may proceed.

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

Commit: `feat: instrument debugger alpha readiness`
