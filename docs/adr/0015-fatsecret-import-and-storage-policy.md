# ADR 0015: Retain Only Permitted FatSecret Identifiers

## Status

Accepted on 2026-08-23.

## Context

FatSecret distinguishes fields that may be stored indefinitely from response
content that must not become durable application state. Its current storable
data guidance names `auth_secret`, `auth_token`, and a fixed set of identifier
fields. Other provider response data may be cached for no more than 24 hours,
and the platform terms require stored data to be removed or replaced when its
designation or the applicable licence changes.

The previous FitnessApp implementation persisted provider meal names, dates,
macronutrients, weights, source hashes, derived insights, and replayable work.
Those copies could survive the source connection and made it impossible to
demonstrate identifier-only retention. A time-limited provider-content cache is
not required by the product, so operating one would add compliance and
invalidation risk without providing a necessary capability.

## Decision

FitnessApp adopts a stricter no-content-cache policy for FatSecret:

- The only durable provider fields are encrypted `auth_secret` and `auth_token`
  credentials plus `exercise_id`, `food_category_id`, `food_entry_id`,
  `food_id`, `recipe_id`, `recipe_types`, `saved_meal_id`,
  `saved_meal_item_id`, and `serving_id`.
- Every other FatSecret response field, provider aggregate, normalized copy,
  hash, embedding, prompt context, insight, report, memory, event, or queued
  replay derived from that content is restricted. It must not cross a durable
  boundary.
- Provider adapters may inspect restricted response content only in memory
  during the active request. OAuth request state and encrypted connection
  credentials are authentication state, not a provider-content cache.
- Identifier refresh persists only validated allowlisted values in
  `fatsecret_provider_identifiers`. Each row is tied to a generated connection
  epoch. An insert succeeds only while that exact epoch is current, so a late
  provider response cannot repopulate data after disconnect or reconnect.
- Historical day/month synchronization, profile-history synchronization,
  provider-backed canonical nutrition writes, their source-state/event path,
  and scheduled nutrition jobs are retired. A requested month refresh is
  reduced to a current-day identifier refresh. Manual profile and weight paths
  remain local and use explicit `MANUAL` origin.
- V32 removes legacy restricted rows and ambiguous downstream projections for
  affected owners, backfills only identifiers available in an allowed legacy
  shape, and installs database guards against future legacy day, food, or
  FatSecret-weight writes. V1-V31 remain immutable.
- Disconnect first locks the owner and then, in one local transaction, removes
  the connection, credentials, identifier rows, legacy provider source state,
  provider weights, provider-backed AI/memory projections, relevant durable
  jobs, event publications, and pending Telegram outbox rows. Manual weights,
  local profile data, notes, and workout data survive.
- The module export manifest includes encrypted-credential and permitted-ID
  retention disclosures, exports permitted IDs without connection epochs, and
  explicitly reports restricted response content as `NOT_STORED` and
  `NOT_RETAINED`. Secrets and restricted content are omitted.
- Disconnect and account deletion describe only local deletion. They do not
  claim remote FatSecret erasure or recall of Telegram messages already
  delivered. Backups remain subject to the documented operational retention
  policy and must never be used to make restricted provider content available
  again.
- If FatSecret withdraws a storage designation or the licence terminates, the
  affected credentials and identifiers are deleted even when this ADR
  currently classifies them as permitted.

ADR 0016 remains authoritative for workout source-state convergence. Its
provider-backed nutrition canonical/event path is superseded by this decision.

## Consequences

- FitnessApp no longer offers durable FatSecret meal history, macro totals,
  imported provider weights, or AI reports derived from provider nutrition.
- FatSecret refresh is useful only for allowlisted identifier capture; provider
  content disappears when the request ends.
- Conservative migration and disconnect cleanup may remove mixed-provenance
  AI, memory, event, job, and outbox rows when reliable lineage is unavailable.
- Connection epochs and database constraints make retention correctness
  independent of normal request timing, but all future provider endpoints must
  still pass through `ProviderDataRetentionPolicy` before persistence.
- A pre-V32 backup is an operational rollback artifact only. Any restoration
  must remain isolated and reapply V32 before traffic, workers, exports, or
  application reads are enabled.

## External Evidence

- [FatSecret storable data guidance](https://platform.fatsecret.com/docs/guides/storable-data)
- [FatSecret Platform API terms](https://platform.fatsecret.com/terms)
- [FatSecret `food_entries.get` v2 response storage labels](https://platform.fatsecret.com/docs/v2/food_entries.get)
