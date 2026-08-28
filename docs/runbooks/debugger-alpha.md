# Debugger Alpha dogfood runbook

## Readiness status

`ALPHA_ENGINEERING_READY: GREEN (2026-08-28)`

This status authorizes neither production rollout nor a product-validation claim. Update it only
after the Phase 1.7 automated gate has passed on the exact revision being released. Product
validation is tracked separately in
[`../validation/debugger-alpha-product-gate.md`](../validation/debugger-alpha-product-gate.md).

## Safety and operating constraints

- The complete manual workflow is the default and must remain useful without an AI provider.
- `app.ai.experiment-draft-enabled=false` is the default. Keep it false during alpha dogfood unless
  an explicit safety review authorizes a controlled proposal-draft trial.
- The backend owns versions, lifecycle transitions, evidence calculations, and the final decision.
  An AI result is only an editable draft and never starts or changes an Experiment.
- Use one in-flight Experiment per owner. Do not repair state by editing database rows manually.
- Telegram dogfood is private-chat only and requires the current account link. Account unlink and
  deletion fence pending private deliveries.
- Do not paste medical emergencies, secrets, or another person's data into notes or prompts. The
  debugger does not diagnose or prescribe treatment.

## Preconditions

1. Apply all Flyway migrations and confirm application health.
2. Create a dogfood account and use its authenticated REST session, or link its private Telegram
   chat with `/link`.
3. Confirm `app.ai.experiment-draft-enabled=false` and sensitive external AI egress is disabled.
4. Pick one measurable problem, one primary metric, a 7–14 day intervention, and an explicit stop
   condition. Record every action performed outside FitnessApp in the daily check-in note.
5. Generate a new `Idempotency-Key` for each distinct REST command. Reuse the same key only when
   retrying the exact same command.

## Manual dogfood flow

### 1. Establish the problem and goal

Telegram shortcut:

```text
/goal Improve bench strength
/goal activate
```

The first command creates an Investigation and a DRAFT Goal; the second activates that Goal. For
the complete Investigation state audit, run these REST transitions in order (the versions shown
assume a newly created Investigation):

```http
POST /api/v1/investigations/{investigationId}/transitions
Content-Type: application/json

{"command":"COLLECTING_BASELINE","expectedVersion":0,"idempotencyKey":"investigation-baseline","reason":"Begin baseline"}

POST /api/v1/investigations/{investigationId}/transitions
Content-Type: application/json

{"command":"READY_FOR_EXPERIMENT","expectedVersion":1,"idempotencyKey":"investigation-ready","reason":"Baseline complete"}
```

Save each response version. If it differs from the straight-line values above, use the returned
current version rather than forcing the example value.

### 2. Create and approve the Experiment

Telegram accepts pipe-separated fields:

```text
/experiment Hypothesis | Add one work set | Twice weekly | strength | 7 | 14 | INCREASE | 2.5 | Stop if pain increases
/experiment propose
/experiment accept
/experiment start
```

After start, move the Investigation to `EXPERIMENTING` through REST:

```http
POST /api/v1/investigations/{investigationId}/transitions
Content-Type: application/json

{"command":"EXPERIMENTING","expectedVersion":2,"idempotencyKey":"investigation-experimenting","reason":"Experiment active"}
```

Save the returned aggregate version after every transition; it is the next command's
`expectedVersion`.

### 3. Record intervention evidence

Submit one check-in for every intervention day. Missing days remain `UNKNOWN`; they are never
silently treated as non-adherence.

```text
/checkin YES | 1 | 7 | 7 | 7 | Added one planned set; no other change
```

Accepted adherence values are `YES`, `NO`, `PARTIAL`, and `UNKNOWN`. The numeric value and the
readiness, sleep, mood, and note fields are optional. Keep notes short and include relevant
out-of-app actions or confounders.

### 4. Record the primary outcome and complete

```text
/outcome strength | 100 | 105 | kg | 2 | 2 | Same measurement protocol
/experiment complete
/evaluate
```

The outcome metric must exactly match the Experiment primary metric. Completion is rejected until
the primary outcome exists. Evaluation is deterministic: coverage, adherence, freshness,
confounders, and effect are calculated by the backend. Copy `evaluationId` from the evaluation
response, choose the explicit user Decision, and close the lifecycle in this order (versions assume
the straight-line first cycle):

```http
POST /api/v1/experiments/{experimentId}/decision
Content-Type: application/json

{"evaluationId":123,"decision":"KEEP","note":"Continue this intervention","idempotencyKey":"experiment-decision"}

POST /api/v1/experiments/{experimentId}/transitions
Content-Type: application/json

{"command":"EVALUATED","expectedVersion":4,"idempotencyKey":"experiment-evaluated","reason":"Decision recorded"}

POST /api/v1/investigations/{investigationId}/transitions
Content-Type: application/json

{"command":"RESOLVED","expectedVersion":3,"idempotencyKey":"investigation-resolved","reason":"Cycle evaluated"}
```

Replace `123` and every placeholder with the returned identifiers. On a version conflict, fetch the
aggregate and use its current version only after reviewing the winning state.

### 5. Inspect the result

Confirm all of the following before counting the run as engineering dogfood:

- the Experiment has one intervention, an outcome, an Evaluation, and a user Decision;
- every intervention date is represented by a check-in or visibly reported as missing;
- reason codes and calculation inputs reproduce the displayed recommendation;
- a repeated REST command with the same idempotency key replays the durable result without a
  duplicate row; a repeated Telegram update returns `COMMAND_ALREADY_PROCESSED` without a
  duplicate row;
- export V2 contains the complete `experiment` fragment and account deletion removes it;
- no raw command, note, outcome, prompt, owner identifier, username, or email appears in metric
  tags or application logs.

## Failure recovery

| Symptom | Recovery |
|---|---|
| Duplicate or timed-out request | Retry the exact payload with the same idempotency key. |
| Version conflict | Fetch the aggregate, review the winning state, then issue a new command with its current version and a new key. |
| Another Experiment occupies the in-flight slot | Complete, abort, or reject the current in-flight Experiment before accepting another. |
| Completion reports missing evidence | Record the primary outcome, then retry completion with the current version. |
| Evaluation is `INCONCLUSIVE` | Preserve the result and reason codes. Do not rewrite evidence; prepare a corrected second-cycle Experiment. |
| Telegram command is rejected | Check private-chat/account-link status and the documented pipe-field count; retry as a new update. |
| Account was unlinked or deleted during delivery | Relink only after confirming account ownership. Never bypass the owned outbox fence. |
| AI provider is unavailable | Continue manually. Do not enable egress or weaken validation to recover the workflow. |

## Engineering gate evidence

Fill this table only from completed command output on the release candidate:

| Gate | Result | Evidence |
|---|---|---|
| `mvn test` | GREEN | 620 tests; 0 failures, 0 errors, 0 skipped. |
| `mvn verify -Pintegration` | GREEN | 620 unit and 124 PostgreSQL integration tests; 0 failures or errors. |
| `mvn test -Parchitecture` | GREEN | 8 checks; 0 failures or errors, 1 explicitly skipped. |
| `mvn dependency:analyze` | GREEN | No dependency problems found. |
| privacy scan | GREEN | Repository Stop-hook scan completed with no finding. |
| Graphify rebuild and clean patch check | GREEN | 4,259 nodes, 8,061 edges, 389 communities; patch whitespace check clean. |
