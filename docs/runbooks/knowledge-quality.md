# Knowledge quality signals

Iteration 2.6 adds aggregate signals to the existing Micrometer registry. No meter contains
owner/Claim/consumer/source IDs, values, prompt text or provider error messages. New tag values
come only from fixed enums. Existing creation, action, conflict, drift and usage names remain stable.

| Meter name | Tags | Meaning |
|---|---|---|
| `fitnessapp.knowledge.claim.provenance` | `origin`, `coverage` | Created Claims with `EVIDENCE_LINKED` versus `SOURCE_ONLY` provenance |
| `fitnessapp.knowledge.claim.usage.trust` | `purpose`, `origin`, `verification`, `aiTrust` | Verification and AI confirmation state at durable use, not the Claim's later state |
| `fitnessapp.knowledge.context.assembled` | `purpose`, `state` | Assembled contexts marked `CLEAR` or `CONFLICT` (including disputed content) |
| `fitnessapp.knowledge.rebuild.converged` | none | Committed generation activations whose source/version/hash set matched canonical allowed sources |
| `fitnessapp.knowledge.rebuild.failed` | `reason` | Failed started builds, using `ProjectionFailureException.Code` only |

These are event counters, not a current inventory or an immutable audit ledger. Restarts can
reset them; absent series mean no recorded events, not verified zero usage. Prometheus exports
normalize dots to underscores and append `_total` to counters.

## Interpretation and targets

- Evidence-link coverage is `EVIDENCE_LINKED / (EVIDENCE_LINKED + SOURCE_ONLY)` by origin.
  A required source reference exists for every valid Claim. Additional evidence links do not
  prove their contents or independence; user assertions can legitimately be source-only.
- `AI_HYPOTHESIS` use is visible separately from other origins. `aiTrust=CONFIRMED` requires
  both `SUPPORTED` verification and `USER_CONFIRMATION`; all other AI states are `UNCONFIRMED`.
- The target for `purpose=EXPERIMENT_DECISION, aiTrust=UNCONFIRMED` is zero. Any recorded event
  is actionable. Disputed usage is similarly visible via `verification=DISPUTED`; consumer guards
  reject it. The general usage recorder measures actual writes rather than hiding policy violations.
- Conflict-context share measures assembly requests carrying a contradiction/dispute signal.
  It is not a count of distinct conflicts, sent warnings or human impressions. Existing
  `claim.conflict` counters measure newly persisted/reopened conflict notifications instead.
- Rebuild convergence rate uses committed successes versus failures of started builds. Requests
  rejected before a generation starts are outside this denominator. `SOURCE_CHANGED` failures are
  expected under concurrent edits; they preserve the old active generation and merit a fresh attempt.

Creation, usage and successful activation counters increment only after their transaction commits.
Rolled-back writes and idempotent usage replays do not inflate them. Build failures count even if
cleanup itself fails. Metric collection reuses registry caching, with no separate counter cache.

## Freshness boundary

Context assembly validates owner-scoped targets before optional retrieval, then refreshes canonical
state after that external lookup. Local source high-watermarks/deletion receipts filter stale sources
before conflict detection or narrative selection. Supported AI-origin Claims without user confirmation
are excluded. These checks are not a lock held across a model call: durable consumers still revalidate.
Local receipts can prove only changes known to this system, not arbitrary unobserved external changes.

Projection/lifecycle audit and operations are documented in [knowledge-rebuild.md](knowledge-rebuild.md).
Final Phase 2 evidence is recorded in the [phase plan](../superpowers/plans/2026-08-12-phase-2-trustworthy-context.md).
