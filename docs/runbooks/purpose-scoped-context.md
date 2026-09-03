# Purpose-scoped personal context (V1)

Iteration 2.3 exposes the internal `knowledge::context-api` `UserContextQuery`.
It does not yet change a Telegram answer or an experiment decision; consumer
integration is Iteration 2.6. Callers resolve the owner at their authenticated
boundary, never from AI output or a client-supplied owner field.

## Slices and ownership

| Purpose | Canonical slices | Optional narratives |
|---|---|---|
| `EXPERIMENT_DRAFT` | Active goals, selected experiment if supplied, supported constraints/facts, observation identities, prior evaluations | Allowed |
| `EXPERIMENT_EVALUATION` | Required selected experiment, supported constraints/facts, observation identities, prior evaluations | Never |
| `TELEGRAM_ANSWER` | Active goals, supported constraints/facts, observation identities | Allowed; no experiment protocol/evaluation slice |

The period is inclusive and limited to 366 days. A supplied goal or experiment
must belong to the owner; when both are supplied they must be related. Selecting
an experiment also scopes goals and prior evaluations to its goal. Otherwise
active goals and period-matching prior evaluations are owner-wide. Missing
active goals or evaluations are reported, not invented.

Knowledge's adapter imports only the flattened `experiment::query-api` contract.
Experiment has no dependency on knowledge. Its read-only query uses the existing
goal, experiment and evidence repositories and neutral evidence contributors,
not `AlphaExperimentContextService` (which deliberately persists evidence refs).
Evidence requests without an experiment use a null correlation subject; manual
check-in contributors then return an empty slice, not an arbitrary experiment's
check-ins. Nutrition/workout contributors remain owner-and-date scoped.

Observations are content-free source/version/hash identities, **not metric
values or causal conclusions**. The current evaluation formula remains owned by
experiment. Prior evaluations retain formula version, decision, data quality,
effect, coverage/adherence, reason codes and timestamp.

## Trust, time and deduplication

- Only `SUPPORTED` claims enter canonical facts. Supported predicates beginning
  with normalized `constraint.` enter the separate verified-constraints slice.
  This is an explicit namespace convention, not free-text classification.
- Open persisted conflicts exclude both claim IDs. `DISPUTED`, `REFUTED`,
  expired, superseded, not-yet-valid and future-observed claims are excluded with
  stable reasons. Conflict detection itself arrives in Iteration 2.5.
- Claims must overlap the requested period and remain valid at assembly time.
  This is current decision context, not a historical time-travel reconstruction.
  Unknown validity bounds stay unknown.
- Origin, verification, confidence basis/value, source/version/hash, validity,
  age and distinct evidence identities remain visible. Model confidence never
  promotes a claim. Claim age over 30 days is labeled stale, not silently erased.
- Observation coverage lists missing dates for nutrition/workout and, when an
  experiment is selected, manual check-ins. No source rows means no freshness
  value; latest source age over 3 days is labeled stale. Future observations are
  excluded. Repeated source IDs retain the newest version deterministically.
- Canonical duplicates collapse by content hash. Different facts from the same
  source version remain distinct (including safety constraints).
  Optional narratives also collapse transitive shared evidence and
  `KNOWLEDGE_CLAIM` echo chains into one item. Repetition never adds confidence or
  independent evidence. A narrative identical to a canonical fact is suppressed.

## Optional search and budget

Canonical SQL reads run first. The assembler suspends any surrounding
transaction; the experiment read is a short read-only repeatable-read
transaction. Optional search runs after these reads, outside their transactions.
There is no network call in the canonical path.

`ContextNarrativeSearch` is an optional projection SPI, implemented by the memory
adapter since Iteration 2.4. Its absence or failure produces a valid canonical result with
`projectionAvailable=false`. Evaluation contexts never invoke it. Provider
exception messages and user content are not logged.

Search returns only claim ID, aggregate version and content hash. Results must
match the owner's eligible canonical row exactly; stale, foreign and unknown
results cannot supply text. Only non-constraint `PROPOSED`/`INCONCLUSIVE` claims
are candidates. Their verification remains visible in the narrative slice.

Ranking is deterministic: explicit user confirmation first, observed time
descending, claim ID ascending. Narrative count is 0–20. The 0–16,000 token
budget conservatively charges one unit per UTF-8 byte of subject, predicate and
value text. Oversized items are skipped, later fitting items may fill the budget,
and truncation is reported. This bounds **optional narrative text**, not the
entire serialized context or a provider prompt. Canonical safety constraints are
never dropped to fit this budget; final consumer prompt budgets remain the
consumer's responsibility.

## Side effects and remaining work

Assembly writes neither claim usage nor experiment evidence references. Usage
belongs after the final answer/decision is durably committed, with purpose and
consumer ID; merely retrieving a claim is not usage. Consumers must revalidate
the relevant source versions at their durable boundary rather than treat a
read snapshot as a concurrency fence.

Iteration 2.4 supplies [versioned projections and atomic rebuild](versioned-memory-projections.md)
through the optional narrative SPI. Iteration 2.5 [detects conflicts](claim-consistency.md)
from canonical state independently of notification acknowledgement/dismissal; Iteration 2.6
connects durable consumers.
