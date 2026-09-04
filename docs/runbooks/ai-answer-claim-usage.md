# Purpose-scoped Telegram answers and exact Claim usage

Iteration 2.6, third checkpoint. AI experiment drafts and the final Phase 2 audit remain open.

`AiContextService` reads `knowledge::context-api` instead of legacy vector memories as facts.
Telegram context requests the last 30 UTC dates with `TELEGRAM_ANSWER`, three optional narratives
and a 2,000-token narrative budget. These limits do not cap the structured context as a whole.
Canonical constraints, supported Claims and unconfirmed narratives retain separate labels;
Claim/goal text is wrapped as untrusted data. Rejected conflicting/disputed content is omitted.
Sensitive-egress permission is checked before assembly can request optional embeddings.

The compatibility `buildMemoryContext` method also supplies this current supplementary context
to daily/weekly/monthly reports; report-period observations still come from their existing event
snapshots. Its legacy semantic-query argument no longer drives vector retrieval. Narrative search
uses the existing purpose-scoped query, not the user's question. Reports have not acquired the
Telegram exact-citation/atomic-delivery contract in this checkpoint.

## Output contract

`telegram-ask-v2.md` asks for at most 20 unique `citedClaimIds`: only Claims the model declares
it used, not every retrieved entry. IDs must belong to the supplied context. The server resolves
their exact canonical version/hash from that context, never from model-supplied hashes.
Empty citations allow a general answer and create no usage rows. Model declarations are not
proof of the model's internal reasoning; omission cannot be detected reliably by this protocol.

After provider I/O, `AiAnswerDeliveryService` starts one transaction:

```text
lock owner + revalidate cited Claims
  -> enqueue existing owned Telegram outbox chunks
  -> snapshot exact Claim usage -> commit
```

Validation checks ownership, current version/hash, source deletion/high-watermark, active validity,
verification and current contradictions. Invalid citations suppress the generated answer. Model
confidence cannot make an AI-origin Claim supported: supported AI hypotheses require user confirmation.
Proposed/inconclusive non-constraint narratives can be cited only with a server-added hypothesis warning.
Conflicts known at assembly or discovered at delivery cause an explicit server-added warning even
when no Claim is cited. A cited conflicting Claim instead rejects the generated answer.

`OwnedMessageOutbox` reuses the existing transactional queue and exact active owner/chat check.
All chunks and usage commit together; revoked links produce no usage. No network calls occur in
this transaction. The existing worker sends only committed rows.

Usage purpose is `AI_ANSWER`, consumer ID `telegram-answer:<first-outbox-id>`, with V41 Claim
version/hash snapshots. This means *durably queued*, not confirmed delivered. No prompt or answer
text is copied into usage. Existing outbox retention can remove the answer before usage; it is not
a permanent answer archive. Forgetting a Claim removes its usage, not previously queued answer text;
that text follows existing outbox retention. Account deletion removes owned queue rows and usage.
Existing lifecycle exports cover both categories.

## Core verification

The existing provider-transaction integration suite covers queued output and exact usage, rollback,
late conflict warning, unknown/stale citations, link revocation and owner deletion. Focused unit
checks cover egress denial, scoped assembly, prompt rendering and Telegram success/fallback paths.
Providers and embeddings are mocked; no live AI or Telegram requests are needed.
