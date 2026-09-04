# Purpose-scoped AI answers and exact Claim usage

Iteration 2.6, Telegram and report checkpoints. The final Phase 2 audit remains open.

`AiContextService` reads `knowledge::context-api` instead of legacy vector memories as facts.
Telegram context requests the last 30 UTC dates with `TELEGRAM_ANSWER`, three optional narratives
and a 2,000-token narrative budget. These limits do not cap the structured context as a whole.
Canonical constraints, supported Claims and unconfirmed narratives retain separate labels;
Claim/goal text is wrapped as untrusted data. Rejected conflicting/disputed content is omitted.
Sensitive-egress permission is checked before assembly can request optional embeddings.

Daily/weekly/monthly reports retain the same prepared context and its canonical Claim identities;
the obsolete string-only `buildMemoryContext` compatibility method was removed. Report-period
measurements still come from their existing source snapshots, not the current 30-day supplementary
context. Narrative search uses the existing purpose-scoped query, not the user's question.

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

## Daily, weekly and monthly reports

The three v2 report prompts request the same exact `citedClaimIds` contract. Previous AI outputs
are explicitly not evidence; associations must not be presented as demonstrated causation.
All three report services pass their prepared context into the existing transactional
`AiInsightPersistenceService`, which reuses `AnswerClaimUsage` after the source lock (and, for
daily reports, the existing exact source-state fence).

Reports use abstention rather than adding a warning: unknown/stale/deleted citations, cited
unconfirmed Claims, or conflicts known at assembly/save reject the write and event publication.
The previous report, if any, is preserved. Empty citations can still describe period measurements
when there is no known conflict. As with interactive answers, omitted model citations cannot be
reliably detected, and retrieved Claims are never automatically counted as used.

One transaction saves the report, its random `ai-insight:<UUID>` output revision in
`metadata.claim_usage_consumer`, exact `AI_ANSWER` usage, and the existing event publication.
The revision distinguishes successive outputs even when the report row is overwritten. A fresh
snapshot replay skips generation and creates no new usage; rolled-back output creates none.
This records a durably saved report, not successful Telegram delivery. Subsequent delivery uses
the existing event/outbox workflow, without recording a second use.

Existing reports are historical outputs, not continuously revalidated current advice. Their
retention/export rules are unchanged; no usage is backfilled for legacy reports. Replacing/deleting
a report may leave historical usage without retained answer text. Forgetting a Claim removes its
usage but does not redact old report text; account deletion removes both. No new table or provider
call is introduced by the usage commit.

## Core verification

The existing provider-transaction integration suite covers queued output and exact usage, rollback,
late conflict warning, unknown/stale citations, link revocation and owner deletion. Focused unit
checks cover egress denial, scoped assembly, prompt rendering and Telegram success/fallback paths.
Providers and embeddings are mocked; no live AI or Telegram requests are needed.
The same PostgreSQL suite also checks report output/usage atomicity, replay, rollback, source
dispute, unknown citations, conflict abstention, unconfirmed-AI rejection and deletion. Existing
daily source-concurrency tests retain their original source-change and deleted-owner fences.
