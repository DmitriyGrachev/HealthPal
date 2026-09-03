# Memory Inspector API

The Inspector reads canonical PostgreSQL claims and works without AI providers.
Every route requires authentication; the owner comes from `CurrentUserApi`.
Request `userId`, origin, verification, and evidence fields cannot grant ownership
or fabricate trusted provenance.

| Operation | Route | Request |
|---|---|---|
| List all current-user claims, including history | `GET /api/v1/knowledge/claims` | none |
| Claim with connected supersession history | `GET /api/v1/knowledge/claims/{id}` | none |
| Explicit user confirmation | `POST /api/v1/knowledge/claims/{id}/confirm` | command body |
| Dispute | `POST /api/v1/knowledge/claims/{id}/dispute` | command body |
| Correct | `PUT /api/v1/knowledge/claims/{id}` | correction body |
| Forget | `DELETE /api/v1/knowledge/claims/{id}?expectedVersion=0` | `Idempotency-Key` header |
| Persisted open conflicts | `GET /api/v1/knowledge/conflicts` | none |

Command body:

```json
{"expectedVersion":0,"idempotencyKey":"unique-command-key"}
```

Correction body:

```json
{
  "subject":"self",
  "predicate":"training_preference",
  "value":{"type":"TEXT","canonicalValue":"morning","unit":null},
  "observedAt":"2026-09-03T12:00:00Z",
  "validFrom":null,
  "validUntil":null,
  "expectedVersion":0,
  "idempotencyKey":"unique-correction-key"
}
```

Values support `TEXT`, `BOOLEAN`, `INTEGER`, `DECIMAL`, `DATE`, and `INSTANT`.
The value is transmitted as a string with an explicit type. Numeric requests use
plain notation, at most 100 integer and 100 fractional digits, and optional units.
Subject/predicate/value limits are 256/128/4000 characters; command keys are
nonblank and at most 128 characters. `expectedVersion` is required and nonnegative.

Responses expose source, evidence references, observation/validity timestamps,
origin, verification, temporal status, confidence basis, version, and supersession
IDs. Detail returns `{ "claim": ..., "history": [...] }`.

An identical command key must be retried with identical input. Changed command
input or a stale aggregate version returns `409` with `IDEMPOTENCY_CONFLICT` or
`VERSION_CONFLICT`. Invalid input returns `400 VALIDATION_ERROR`; inaccessible
and missing claims both return `404 NOT_FOUND`. Forget returns `204`.

Correction preserves the prior claim as superseded and records a new explicit
user assertion. Forget is destructive: it erases the connected correction history
and its evidence/usage/conflict links, not just the currently displayed version.
Account deletion also removes non-content deletion audit metadata.
Internally, owner-scoped source/key digests prevent replay without retaining
plaintext source identifiers or command keys for forgotten claims. Export exposes
only deletion IDs, times, and schema metadata, not those internal digests.

This iteration provides the usage recorder and persisted conflict query only.
Purpose-scoped context consumption, claim vector projections/rebuild, and automatic
conflict detection follow in Iterations 2.3–2.6; they are not enabled by this API.
