# Experiment result Claims

Iteration 2.6, first checkpoint: deterministic Evaluation to Knowledge integration.
AI/Decision integration is now implemented; final gate evidence is in the
[Phase 2 plan](../superpowers/plans/2026-08-12-phase-2-trustworthy-context.md).

## Source and trust

A newly inserted Evaluation publishes `ExperimentEvaluationCompletedEvent` in its
database transaction. The event contains owner/evaluation identifiers, deterministic
classifications and evidence identities/hashes, not hypotheses, notes or measured values.
Failure to construct canonical provenance aborts the Evaluation transaction.

The calculator and provenance use the same check-in rows. Their IDs and the primary
Outcome ID are saved in the existing Evaluation calculation-input snapshot. Later
check-ins cannot change the result, its evidence or the event hash. Existing evaluations
without this snapshot are not backfilled: their exact historical evidence is unknown.

The knowledge listener runs after commit, locks the owner and rereads the exact source
before writing. Missing/deleted/mismatched sources are ignored. Repeated delivery converges
to one Claim; forgetting that Claim leaves a tombstone that prevents replay resurrection.
Owner deletion fences an in-flight replay. No provider call is needed for this path.

The Claim is scoped to `experiment:<id>` / `observed_effect`, with `EXPERIMENT_RESULT`
origin. Only V1, sufficient data, a known effect, a conclusive recommendation and no
unresolved confounder qualify as `SUPPORTED`; other evaluations produce `PROPOSED`.
Support describes the recorded experiment result, not a general causal or medical fact.
The evidence confidence score denotes this deterministic classification, not a calibrated
probability of causation. It does not confirm an AI hypothesis or a user's Decision.

## Boundaries and lifecycle

`knowledge -> experiment::api` exposes the event and owner-scoped source read contract.
Experiment imports no knowledge types and stores no Claim foreign key. The source
Evaluation remains immutable; no migration or extra persistence table is introduced.

Existing experiment exports include the calculation-input snapshot. Knowledge exports
include the Claim and its evidence. Event publications inherit the existing owner FK,
exported receipt metadata and account deletion cascade. Provider and backup retention
limitations are unchanged.

Optional vector projection remains independently guarded. Denied external egress can
leave its publication pending without rolling back the canonical result Claim; see
[versioned projections](versioned-memory-projections.md). The core AI-disabled workflow
test disables this optional listener, while dedicated projection/lifecycle tests cover it.

## Verification scope

The existing manual workflow now has two core parameter cases: sufficient/insufficient
coverage. It checks source stability after late check-ins, duplicate delivery, forgetting
and account-deletion replay. The existing Evaluation unit test checks persisted source
IDs and event publication; module tests forbid the reverse Experiment-to-Knowledge edge.

Exact Decision usage, purpose-scoped AI consumers, proposed-only AI hypotheses, durable
AI-answer usage, final epistemic metrics and the aggregate Phase 2 audit remain in 2.6.
