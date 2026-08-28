# Debugger Alpha product-validation ledger

## Status

`ALPHA_PRODUCT_VALIDATED: OPEN`

Engineering readiness, automated tests, fixtures, and agent-generated runs cannot close this
ledger. Only observed behavior from real target users may provide evidence. Phase 2 engineering may
continue after `ALPHA_ENGINEERING_READY`; product rollout claims remain blocked while this ledger is
open.

## Evidence rules

- Record dated, privacy-safe aggregate counts and a non-personal observation reference.
- Do not store names, usernames, email addresses, Telegram identifiers, free-text notes, nutrition
  details, prompts, or raw Experiment content in this file.
- A person is counted once per criterion. Synthetic accounts and team retries are excluded unless
  the criterion explicitly asks for owner dogfood.
- A started Experiment has entered `ACTIVE`. Reaching Evaluation means a durable deterministic
  Evaluation exists. A second cycle must be started voluntarily, without a direct prompt.
- For out-of-app actions, record only completeness (`recorded/expected`) and the observation
  reference, never the action text.

## Exit criteria

| Criterion | Required evidence | Current evidence | Status |
|---|---|---|---|
| Owner dogfood reaches Evaluation and records every out-of-app action | One owner run; action-log completeness `expected = recorded` | None | OPEN |
| External target users use the workflow | 3–5 distinct target users observed using it | 0 | OPEN |
| Target users start an Experiment | 5 distinct target users reach `ACTIVE` | 0 | OPEN |
| Evidence-bearing cycle reaches Evaluation | At least 3 target users complete a 7–14 day cycle and reach Evaluation | 0 | OPEN |
| Voluntary second cycle | At least 2 target users start a second cycle without prompting | 0 | OPEN |
| First-seven-day intermediate value and cold start | Dated observation shows the promised intermediate value and acceptable baseline behavior during days 1–7 | None | OPEN |

## Observation log

Add rows only after a real observation. Keep references non-personal and store sensitive source
material in the approved research repository, not in Git.

| Date (UTC) | Criterion | Aggregate result | Observation reference | Reviewer |
|---|---|---|---|---|
| — | — | — | — | — |

## Closure record

Leave blank until every criterion above is green and independently reviewed.

- Closed at: —
- Evidence review: —
- Decision owner: —
- Notes: —

