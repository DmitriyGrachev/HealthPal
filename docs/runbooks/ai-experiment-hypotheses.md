# AI experiment hypotheses

Iteration 2.6, fourth checkpoint. The optional draft generator is still disabled by default;
no endpoint, automatic Experiment transition or agent runtime is introduced.

The generator checks sensitive-egress permission before context assembly/optional embeddings.
It requests `EXPERIMENT_DRAFT` context from baseline start through baseline end plus duration,
with three optional narratives and a 2,000-token narrative budget. Structured constraints,
supported Claims and unconfirmed narratives remain separate, untrusted prompt data. Current
contradictions/disputed context cause abstention. The v2 prompt preserves the existing safety,
immutable calculation fields and evidence-ID validation rules.

Generation suspends any caller transaction. Provider failure keeps the manual flow available.
After semantic validation, Knowledge starts a short transaction to persist only the hypothesis:

1. Lock the owner and unchanged owned Experiment/active Goal versions.
2. Revalidate supplied Claim identities and reject current contradictions.
3. Reread selected evidence identities from canonical owner-scoped sources.
4. Write `AI_HYPOTHESIS / PROPOSED` through existing Claim commands and deletion fences.

The Claim uses subject `experiment:<id>`, predicate `proposed_hypothesis`, source
`AI_EXPERIMENT_DRAFT:<experiment-id>`, and source version `experiment-version + 1`.
Confidence basis is `AI_MODEL`; score zero means no evidential confidence is established by
generation, not a measured probability that the hypothesis is false. Provider confidence
never changes verification. The proposed intervention/rationale remain user-editable draft
data; they do not update the Experiment or become independent supported Claims.

One hypothesis is accepted per Experiment version. Identical hypotheses with the same evidence converge; a different
variant at the same version is unavailable rather than silently replacing the first. Explicit
generation identities can be added if a regeneration workflow is introduced. A forgotten
source cannot be restored by replay; owner deletion blocks late persistence. AI regeneration never
replaces a Claim whose verification the user has changed. Existing Claim
export, deletion and projection mechanisms apply, without a new table or migration.

The existing offline provider tests cover permission ordering, contextual prompt rendering,
manual fallback and validated output. One core PostgreSQL scenario runs the actual generator,
context and writer with a mocked provider, including a caller transaction, and checks proposed-only
persistence, unchanged Experiment state, convergence, goal/evidence changes, forgetting and
owner deletion. These tests never call live providers.

This is not the final Phase 2 gate. Remaining work includes the epistemic metrics and lifecycle/
context freshness audit, rebuild runbook, roadmap/diagrams, and complete phase verification.
