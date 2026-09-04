# Stable Base Operations Runbook

## Startup

1. Provide the production variables documented in `src/main/resources/application.properties`.
2. Start the application with the default profile. Flyway must migrate a clean
   PostgreSQL database through the current V41 before the application accepts traffic.
3. For local development use `SPRING_PROFILES_ACTIVE=dev`; the dev profile must point to PostgreSQL and keep `spring.jpa.hibernate.ddl-auto=validate`.
4. Verify `GET /actuator/health` before enabling scheduled workers.

## Supported Platform and Schema Baseline

The verified production baseline as of 2026-08-23 is:

- Java 21;
- Spring Boot 4.1.0 and its managed Spring Framework 7.0.8 line;
- Spring AI 2.0.0;
- Spring Modulith 2.1.0 with the current JDBC publication structure;
- Telegram Bots 10.2.0 on its long-polling/client APIs;
- Phase 0 ended at Flyway V33; the current Stage 2 schema is V41 on PostgreSQL/pgvector;
- Jackson 3 for application JSON. Jackson 2 is restricted to the JJWT
  compatibility boundary and must not appear in application imports.

Before accepting a platform change, run the effective-POM/dependency-tree
inspection and all gates in `TESTING.md`. Do not deploy a mixed Boot 3 /
Spring Framework 6 graph under the Boot 4 application.

Stage 2 adds V34–V36 Experiment state and V37–V41 canonical knowledge, usage and projections.
The historical cutover instructions below retain their original version boundaries. For current
projection recovery and erasure guarantees, use [knowledge-rebuild.md](knowledge-rebuild.md).

## Scheduled Work

- Historical FatSecret content synchronization is disabled. There is no
  scheduled provider-nutrition job and no durable provider-content replay.
  An explicit refresh stores only permitted current-connection identifiers and
  publishes no canonical nutrition event.
- Durable jobs use a PostgreSQL-fenced lease (`lease_owner`, `lease_generation`,
  `lease_expires_at`, and `claimed_at`). Workers recover expired leases using
  PostgreSQL `NOW()`, then claim exactly one due row immediately before
  execution. Heartbeats renew the same generation while provider work runs;
  every completion, failure, and skip is guarded by the original owner and
  generation.
- A retryable expired claim returns to `PENDING`; an expired final attempt is
  terminal `FAILED` with `CLAIM_TIMEOUT_MAX_ATTEMPTS`. Exhausted `PENDING`
  rows are not valid state after V29.
- Telegram outbox workers use one just-in-time fenced claim per provider call.
  The claim carries a stable worker owner, a monotonically increasing generation,
  and an expiry. Recovery never reclaims an expired `SENDING` row: owned rows
  become durable `DELIVERY_UNKNOWN` audit records and anonymous rows are
  deleted. A late provider result is a fenced no-op.

## Recovery

- Inspect `durable_jobs` for `FAILED` rows and retry them through
  `POST /api/v1/jobs/{id}/retry` as the owning user/operator. Manual retry is
  accepted only from `FAILED` or `SKIPPED`; `RUNNING`, `SUCCEEDED`, and other
  states return conflict code `DURABLE_JOB_RETRY_NOT_ALLOWED`. Retry clears
  attempts/backoff and active lease fields but preserves monotonic
  `lease_generation`.
- Fenced stale mutations return `false` and emit only the stable reason code
  `FENCE_REJECTED`. Job failures persist bounded stable codes/details such as
  `PROVIDER_UNAVAILABLE`, `INVALID_PAYLOAD`, and
  `CLAIM_TIMEOUT_MAX_ATTEMPTS`; provider exception messages, payloads, user
  identifiers, idempotency keys, and lease owners are never persisted or
  logged.
- Inspect `telegram_delivery_outbox` for `FAILED` and `DELIVERY_UNKNOWN` rows
  using only stable error codes. A Telegram `429` is a proven rejection and can
  return a live claim to `PENDING` while attempts remain; exhaustion is terminal
  for owned work and anonymous work is deleted. A `400` is permanent except
  for the existing Markdown parse/entity fallback, which may make one plain
  text request under the same live fence. Timeouts, network errors, and 5xx
  outcomes are uncertain and are never automatically resent.
- `DELIVERY_UNKNOWN` is not an automatic retry path. A user or operator who
  explicitly wants another delivery creates a new outbox row, preserving the
  old owner-bound audit row. Never reset its status or lease generation.
- If a deployment stops during a claim, wait for the recovery window before manually changing status. Preserve the original `error_message` when opening an incident.
- Lease fencing protects the durable ledger, not external side effects. The
  system remains at-least-once around provider calls; provider idempotency
  keys are mandatory wherever a provider supports them.

## Event Publication Recovery

- Flyway owns the Spring Modulith JDBC schema. Runtime schema initialization is
  disabled and `spring.modulith.events.jdbc.use-legacy-structure=false`.
- V33 adds `status`, `completion_attempts`, and
  `last_resubmission_date`. Pre-upgrade incomplete rows become `FAILED`;
  completed rows become `COMPLETED`; both start with one recorded attempt.
- Restart replay remains enabled for outstanding publications. There is no
  generic public replay endpoint. Any failed-publication maintenance tooling
  must use bounded `IncompleteEventPublications`/`ResubmissionOptions`;
  never update publication state or replay serialized payloads with ad-hoc SQL.
- A successful resubmission becomes `COMPLETED`, increments
  `completion_attempts`, and records `last_resubmission_date`. Repeating the
  operation does not redeliver an already completed publication.
- Completed publications are retained as an account-scoped audit/replay
  ledger. Export exposes safe receipt metadata only, never
  `serialized_event`; account deletion removes rows by enforced `user_id`.
- Inspect only aggregate counts/statuses. Never print or log
  `serialized_event`, listener payloads, user IDs, or source metadata during
  recovery.

## Observability

- Actuator metrics expose only aggregate operational gauges: `fitnessapp.durable_jobs.pending`, `fitnessapp.durable_jobs.failed`, `fitnessapp.telegram_outbox.pending`, `fitnessapp.telegram_outbox.failed`, and `fitnessapp.event_publication.incomplete`.
- Alert on sustained growth of failed or incomplete gauges. Never add message text, prompts, tokens, payloads, or user identifiers as metric tags.

## Shutdown and Deployment

- Stop accepting new traffic, then allow the application to finish the current scheduler/worker tick.
- Spring waits up to 30 seconds for scheduled and async executors. The AI executor waits five seconds for in-flight provider work, then interrupts it; the durable job or outbox state remains the recovery source for the next worker cycle.
- Do not run destructive SQL against `durable_jobs`, `telegram_delivery_outbox`, `event_publication`, or `user_memory` during deployment.
- Flyway migrations are append-only. Roll back by deploying a previous application version only when the new migration is backward compatible; otherwise restore the database backup and follow the incident procedure.

## V31 → V32 FatSecret Retention Cutover

V32 is an intentionally destructive, forward-only provider-retention migration.
It removes restricted FatSecret content and ambiguous downstream projections;
it is not a historical bridge and must never be copied into V1-V31.

1. Stop application traffic, event replay, schedulers, durable-job workers, and
   Telegram outbox workers. Confirm that no process can write or replay data in
   the target schema.
2. Create and verify a restorable, access-controlled database backup. Record
   only its operational identifier and verification result. The backup is a
   rollback artifact, not an application recovery source: if restored, keep the
   database isolated and reapply V32 before enabling traffic, workers, exports,
   or application reads.
3. Set `search_path` to the fixed deployment schema and abort unless the latest
   successful Flyway version is exactly `31`:

```sql
SELECT version
  FROM flyway_schema_history
 WHERE success
 ORDER BY installed_rank DESC
 LIMIT 1;
```

4. Record the following aggregate-only preflight row. Do not retain row values,
   provider payloads, tokens, identifiers, or user IDs:

```sql
WITH affected_users AS (
    SELECT user_id FROM fatsecret_connection
    UNION
    SELECT user_id FROM fatsecret_day
    UNION
    SELECT user_id FROM weight_history WHERE weight_source = 'FATSECRET'
    UNION
    SELECT user_id FROM nutrition_source_state
)
SELECT
    (SELECT COUNT(*) FROM affected_users) AS affected_owners,
    (SELECT COUNT(*) FROM fatsecret_connection) AS connections,
    (SELECT COUNT(*) FROM fatsecret_day) AS restricted_days,
    (SELECT COUNT(*) FROM fatsecret_food) AS restricted_foods,
    (SELECT COUNT(*) FROM weight_history WHERE weight_source = 'FATSECRET')
        AS restricted_weights,
    (SELECT COUNT(*) FROM weight_history WHERE weight_source = 'MANUAL')
        AS manual_weights,
    (SELECT COUNT(*) FROM nutrition_source_state) AS restricted_source_states,
    (SELECT COUNT(*) FROM ai_insights
      WHERE user_id IN (SELECT user_id FROM affected_users)) AS affected_ai,
    (SELECT COUNT(*) FROM user_memory
      WHERE user_id IN (SELECT user_id FROM affected_users)) AS affected_memory,
    (SELECT COUNT(*) FROM event_publication
      WHERE user_id IN (SELECT user_id FROM affected_users)) AS affected_publications,
    (SELECT COUNT(*) FROM durable_jobs
      WHERE user_id IN (SELECT user_id FROM affected_users)
        AND job_type IN ('NUTRITION_SYNC', 'DAILY_INSIGHT', 'WEEKLY_REPORT', 'MONTHLY_REPORT'))
        AS affected_jobs,
    (SELECT COUNT(*) FROM telegram_delivery_outbox
      WHERE user_id IN (SELECT user_id FROM affected_users)) AS affected_outbox;
```

5. Apply V32 through the normal Flyway deployment mechanism. PostgreSQL and
   Flyway own the migration transaction; do not run migration fragments by
   hand, edit `flyway_schema_history`, or use `flyway repair` to conceal a
   failure.
6. Verify that the latest successful version is exactly `32`, the manual-weight
   count equals the preflight value, and this postflight row contains zeros for
   every `invalid_*`, `orphan_*`, and `restricted_*` field:

```sql
SELECT
    (SELECT COUNT(*) FROM fatsecret_day) AS restricted_days,
    (SELECT COUNT(*) FROM fatsecret_food) AS restricted_foods,
    (SELECT COUNT(*) FROM weight_history WHERE weight_source = 'FATSECRET')
        AS restricted_weights,
    (SELECT COUNT(*) FROM nutrition_source_state) AS restricted_source_states,
    (SELECT COUNT(*) FROM fatsecret_connection WHERE connection_epoch IS NULL)
        AS invalid_connection_epochs,
    (SELECT COUNT(*) FROM fatsecret_provider_identifiers identifier
      WHERE identifier.identifier_type NOT IN (
          'exercise_id', 'food_category_id', 'food_entry_id', 'food_id',
          'recipe_id', 'recipe_types', 'saved_meal_id',
          'saved_meal_item_id', 'serving_id'
      )) AS invalid_identifier_types,
    (SELECT COUNT(*) FROM fatsecret_provider_identifiers identifier
      WHERE (identifier.identifier_type = 'recipe_types'
             AND identifier.identifier_value !~ '^[A-Za-z0-9_-]+(,[A-Za-z0-9_-]+)*$')
         OR (identifier.identifier_type <> 'recipe_types'
             AND identifier.identifier_value !~ '^[1-9][0-9]{0,18}$'))
        AS invalid_identifier_shapes,
    (SELECT COUNT(*) FROM fatsecret_provider_identifiers identifier
      WHERE NOT EXISTS (
          SELECT 1
            FROM fatsecret_connection connection
           WHERE connection.user_id = identifier.user_id
             AND connection.connection_epoch = identifier.connection_epoch
      )) AS orphan_identifiers,
    (SELECT COUNT(*) FROM weight_history WHERE weight_source = 'MANUAL')
        AS manual_weights;
```

7. Verify that an attempted FatSecret day/food write and a
   `weight_source = 'FATSECRET'` write are rejected in a rolled-back operator
   test transaction. Do not use real provider content for this check.
8. Enable the application only after the postflight evidence is accepted. Do
   not re-enable historical nutrition workers; V32 deliberately leaves that
   capability disabled.

If V32 fails, keep the application stopped. Confirm that its transaction rolled
back, investigate without modifying old migrations, and restore the verified
backup only when normal database recovery is required. A restored database
must stay isolated until V32 succeeds. Local disconnect or account deletion
does not imply remote FatSecret erasure, and delivered Telegram messages cannot
be recalled by this procedure.

## V32 → V33 Modulith Publication Upgrade

V33 is a forward-only schema expansion for Spring Modulith 2.1. Run it with
traffic, schedulers, and event-producing workers stopped.

1. Verify a restorable backup and confirm that the latest successful Flyway
   version is exactly `32`.
2. Record this aggregate-only preflight; do not select publication payloads or
   owner identifiers:

```sql
SELECT
    COUNT(*) AS publication_rows,
    COUNT(*) FILTER (WHERE completion_date IS NULL) AS incomplete_rows,
    COUNT(*) FILTER (WHERE completion_date IS NOT NULL) AS completed_rows
  FROM event_publication;
```

3. Apply V33 through normal Flyway startup. Do not edit the old table or
   `flyway_schema_history` manually.
4. Before enabling traffic, verify that the latest successful version is
   exactly `33` and that this aggregate-only postflight returns zeros for all
   four invalid counts:

```sql
SELECT
    COUNT(*) FILTER (WHERE status IS NULL) AS null_status,
    COUNT(*) FILTER (WHERE completion_attempts IS NULL) AS null_attempts,
    COUNT(*) FILTER (
        WHERE completion_date IS NULL AND status <> 'FAILED'
    ) AS inconsistent_incomplete,
    COUNT(*) FILTER (
        WHERE completion_date IS NOT NULL AND status <> 'COMPLETED'
    ) AS inconsistent_completed
  FROM event_publication;
```

5. Verify that `event_publication_serialized_event_hash_idx` exists without
   selecting `serialized_event`.

If V33 fails, keep the application stopped and confirm transactional rollback.
Do not use `flyway repair` to conceal the failure. Restore only through the
normal database incident procedure, then reapply V33 before enabling traffic.

## Historical Upgrade Boundaries

Use these procedures only for a database stopped exactly at the stated
Flyway version. Stop application traffic and scheduled/worker writers first,
verify a restorable backup, and record aggregate-only preflight counts. Do not
log note content, Telegram text, payloads, metadata, prompts, tokens, or user
identifiers.

The boundary map is:

- V20 → run `db/upgrade/pre-v21-durable-job-idempotency.sql`, then Flyway V21.
- V22 → run `db/upgrade/pre-v23-absolute-timestamps.sql`, then Flyway V23.
- V24 → run `db/upgrade/pre-v25-domain-invariants.sql`, then Flyway V25.
- V25 → run `db/upgrade/pre-v26-memory-owner.sql`, then Flyway V26.

V27–V31 are normal immutable forward migrations and require no separate bridge:
they add durable-work ownership, purge unsafe ownerless terminal deliveries,
add job/outbox generation-fenced leases, and version domain source state.
V32 and V33 use the dedicated cutover procedures above. Dirty historical
fixtures and clean-latest migration are verified by
`HistoricalUpgradeIntegrationTest`; operator preflight remains mandatory in
production.

### Fail-closed execution boundary

Select one bridge and use the fixed target schema. The operator must query the
latest successful Flyway version and abort unless it exactly matches the
bridge's required boundary. This is read-only verification; never edit or
fake `flyway_schema_history`.

The following PowerShell blocks are directly executable from the repository
root. Run the precheck and bridge block first; it validates the fixed schema
and selected bridge, checks the boundary, and runs the bridge with one
PostgreSQL transaction. Replace only `$selectedBridge` with one of the four
allowlisted values, using the same value in both blocks.

```powershell
if (-not $env:DATABASE_URL) {
    throw 'DATABASE_URL is required'
}

$targetSchema = 'fitnessapp'
$selectedBridge = 'pre-v25-domain-invariants.sql'
$boundaries = @{
    'pre-v21-durable-job-idempotency.sql' = @{ Current = '20'; Next = '21' }
    'pre-v23-absolute-timestamps.sql'     = @{ Current = '22'; Next = '23' }
    'pre-v25-domain-invariants.sql'       = @{ Current = '24'; Next = '25' }
    'pre-v26-memory-owner.sql'            = @{ Current = '25'; Next = '26' }
}

if ($targetSchema -ne 'fitnessapp' -or $targetSchema -notmatch '^[a-z_][a-z0-9_]*$') {
    throw "Refusing unvalidated schema: $targetSchema"
}
if (-not $boundaries.ContainsKey($selectedBridge)) {
    throw "Refusing unselected bridge: $selectedBridge"
}

$expectedCurrent = $boundaries[$selectedBridge].Current
$schemaSql = "SET search_path TO $targetSchema, pg_catalog;"
$historySql = "$schemaSql SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;"

$latest = (& psql -v ON_ERROR_STOP=1 -X -A -t "$env:DATABASE_URL" -c $historySql | Out-String).Trim()
if ($LASTEXITCODE -ne 0) {
    throw 'Could not read the latest successful Flyway version; aborting'
}
if ($latest -ne $expectedCurrent) {
    throw "ABORT: latest successful Flyway version is '$latest'; expected exactly '$expectedCurrent' for $selectedBridge"
}

# Run the matching aggregate-only preflight query below, then execute the bridge.
& psql -v ON_ERROR_STOP=1 --single-transaction "$env:DATABASE_URL" `
    -c $schemaSql `
    -f (Join-Path 'src/main/resources/db/upgrade' $selectedBridge)
if ($LASTEXITCODE -ne 0) {
    throw "Bridge failed and was rolled back: $selectedBridge"
}

$afterBridge = (& psql -v ON_ERROR_STOP=1 -X -A -t "$env:DATABASE_URL" -c $historySql | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $afterBridge -ne $expectedCurrent) {
    throw "Unexpected Flyway history change after bridge: '$afterBridge'"
}

```

After the first block completes successfully, run the approved Flyway
migration through the normal deployment mechanism. Do not `INSERT`, `UPDATE`,
`DELETE`, or repair `flyway_schema_history`. When that migration completes,
run this separately executable, self-contained post-migration verification
block. It is safe to rerun and must use the same `$selectedBridge` value as the
first block.

```powershell
if (-not $env:DATABASE_URL) {
    throw 'DATABASE_URL is required'
}

$targetSchema = 'fitnessapp'
$selectedBridge = 'pre-v25-domain-invariants.sql'
$boundaries = @{
    'pre-v21-durable-job-idempotency.sql' = @{ Current = '20'; Next = '21' }
    'pre-v23-absolute-timestamps.sql'     = @{ Current = '22'; Next = '23' }
    'pre-v25-domain-invariants.sql'       = @{ Current = '24'; Next = '25' }
    'pre-v26-memory-owner.sql'            = @{ Current = '25'; Next = '26' }
}

if ($targetSchema -ne 'fitnessapp' -or $targetSchema -notmatch '^[a-z_][a-z0-9_]*$') {
    throw "Refusing unvalidated schema: $targetSchema"
}
if (-not $boundaries.ContainsKey($selectedBridge)) {
    throw "Refusing unselected bridge: $selectedBridge"
}

$expectedNext = $boundaries[$selectedBridge].Next
$schemaSql = "SET search_path TO $targetSchema, pg_catalog;"
$historySql = "$schemaSql SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;"
$afterMigration = (& psql -v ON_ERROR_STOP=1 -X -A -t "$env:DATABASE_URL" -c $historySql | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $afterMigration -ne $expectedNext) {
    throw "Migration verification failed: latest successful Flyway version must equal exactly '$expectedNext', got '$afterMigration'"
}
```

The bridge scripts do not contain `BEGIN` or `COMMIT`; the caller owns the
transaction. The table locks protect the preflight and normalization from
concurrent writers. If any statement fails, verify that both DML and DDL are
unchanged, restore the verified backup if required, and investigate. Never
use `flyway repair` to conceal or reverse a failed historical migration.

Before and after each operation, retain only operational evidence: UTC start
and finish time, operator/change identifier, the named aggregate counts below,
the command exit code, and the latest successful Flyway version/checksum
output. Do not retain row IDs, row content, or user identifiers.

The checksum evidence is an aggregate deployment record:

```sql
SELECT version, checksum, installed_rank, installed_on
  FROM flyway_schema_history
 WHERE success
 ORDER BY installed_rank;
```

### Aggregate-only bridge preflight and postflight

Run the applicable query at the exact boundary after setting
`search_path` to the target schema. Each query returns one aggregate row and
does not output row IDs, content, or user identifiers. Run the same query
after the bridge, except where the postflight query explicitly checks the
next migration's schema result.

#### V20 → V21: durable-job idempotency

At V20, record these named counts before and after
`pre-v21-durable-job-idempotency.sql`:

```sql
SELECT
    COUNT(*) FILTER (WHERE job.idempotency_key IS NULL) AS null_idempotency_keys,
    COUNT(*) FILTER (
        WHERE job.idempotency_key IS NOT NULL
          AND EXISTS (
              SELECT 1
                FROM durable_jobs missing
               WHERE missing.idempotency_key IS NULL
                 AND job.idempotency_key = 'legacy:' || missing.id::text
          )
    ) AS legacy_base_key_collisions,
    COUNT(*) FILTER (WHERE job.idempotency_key IS NOT NULL)
      - COUNT(DISTINCT job.idempotency_key) AS duplicate_non_null_idempotency_keys
  FROM durable_jobs job;
```

Because `legacy_base_key_collisions` is defined relative to rows that still
have NULL keys, all three postflight counts are exactly zero:
`null_idempotency_keys = 0`, `legacy_base_key_collisions = 0`, and
`duplicate_non_null_idempotency_keys = 0`. Preservation of an occupied legacy
base key and assignment of the lowest free suffix to the repaired row are
covered by integration tests. After Flyway, the PowerShell check above must
report exactly V21.

#### V22 → V23: absolute note timestamps

At V22, record the following before and after
`pre-v23-absolute-timestamps.sql`:

```sql
SELECT
    COUNT(*) AS note_rows,
    COUNT(*) FILTER (WHERE created_at IS NULL) AS null_created_at,
    COUNT(*) FILTER (WHERE updated_at IS NULL) AS null_updated_at
  FROM user_notes;
```

The bridge changes no data, so all three post-bridge counts must equal their
preflight values. After Flyway V23, assert both timestamp columns are
`timestamp with time zone`:

```sql
SELECT
    COUNT(*) FILTER (
        WHERE column_name = 'created_at'
          AND data_type = 'timestamp with time zone'
    ) AS created_at_timestamptz,
    COUNT(*) FILTER (
        WHERE column_name = 'updated_at'
          AND data_type = 'timestamp with time zone'
    ) AS updated_at_timestamptz
  FROM information_schema.columns
 WHERE table_schema = current_schema()
   AND table_name = 'user_notes'
   AND column_name IN ('created_at', 'updated_at');
```

The expected post-migration result is `created_at_timestamptz = 1` and
`updated_at_timestamptz = 1`. Legacy naive note timestamps are interpreted as
UTC by the immutable V23 `AT TIME ZONE 'UTC'` conversion.

#### V24 → V25: domain invariants

At V24, record the following named counts before and after
`pre-v25-domain-invariants.sql`:

```sql
SELECT
    (SELECT COUNT(*) FILTER (WHERE username IS NULL OR length(trim(username)) = 0) FROM users)
        AS blank_usernames,
    (SELECT COUNT(*) FILTER (WHERE email IS NULL OR length(trim(email)) = 0) FROM users)
        AS blank_emails,
    (SELECT COUNT(*) FILTER (
        WHERE calories < 0 OR protein < 0 OR fat < 0 OR carbohydrate < 0
     ) FROM fatsecret_day) AS invalid_day_metrics,
    (SELECT COUNT(*) FILTER (
        WHERE calories < 0 OR protein < 0 OR fat < 0 OR carbohydrate < 0
     ) FROM fatsecret_food) AS invalid_food_metrics,
    (SELECT COUNT(*) FILTER (WHERE external_food_id IS NULL OR external_food_id <= 0)
       FROM fatsecret_food) AS invalid_food_identity,
    (SELECT COUNT(*) FILTER (WHERE name IS NULL OR length(trim(name)) = 0)
       FROM fatsecret_food) AS invalid_food_name,
    (SELECT COUNT(*) FILTER (WHERE meal_type IS NULL OR length(trim(meal_type)) = 0)
       FROM fatsecret_food) AS invalid_food_meal,
    (SELECT COUNT(*) FILTER (
        WHERE goal_weight_kg <= 0 OR last_weight_kg <= 0 OR height_cm <= 0
     ) FROM profile) AS invalid_profile,
    (SELECT COUNT(*) FILTER (WHERE weight_kg IS NULL OR weight_kg <= 0)
       FROM weight_history) AS invalid_weight_readings,
    (SELECT COUNT(*) FILTER (WHERE exercise_name IS NULL OR length(trim(exercise_name)) = 0)
       FROM workout_exercises) AS blank_exercises,
    (SELECT COUNT(*) FILTER (
        WHERE set_index IS NULL OR set_index < 0 OR reps IS NULL OR reps < 0
     ) FROM workout_sets) AS invalid_set_index_or_reps,
    (SELECT COUNT(*) FILTER (WHERE weight < 0) FROM workout_sets)
        AS negative_set_weight,
    (SELECT COUNT(*) FILTER (WHERE content IS NULL OR length(trim(content)) = 0)
       FROM user_notes) AS blank_notes,
    (SELECT COUNT(*) FILTER (
        WHERE type IS NULL OR type NOT IN (
            'ILLNESS', 'TRAVEL', 'INJURY', 'STRESS', 'ALLERGY', 'GOAL',
            'PREFERENCE', 'TRAINING', 'NUTRITION', 'GENERAL', 'MOOD', 'OTHER'
        )
     ) FROM user_notes) AS unknown_note_types,
    (SELECT COUNT(*) FILTER (
        WHERE status NOT IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')
           OR attempts < 0 OR max_attempts <= 0 OR attempts > max_attempts
     ) FROM durable_jobs) AS invalid_durable_states,
    (SELECT COUNT(*) FILTER (
        WHERE status IN ('PENDING', 'RUNNING')
          AND attempts >= max_attempts
          AND NOT (
              status NOT IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')
              OR attempts < 0 OR max_attempts <= 0 OR attempts > max_attempts
          )
     ) FROM durable_jobs) AS exhausted_durable_states,
    (SELECT COUNT(*) FILTER (
        WHERE status = 'RUNNING'
          AND updated_at < CURRENT_TIMESTAMP - INTERVAL '15 minutes'
     ) FROM durable_jobs) AS stale_durable_running,
    (SELECT COUNT(*) FILTER (WHERE text IS NULL OR length(trim(text)) = 0)
       FROM telegram_delivery_outbox) AS blank_telegram_text,
    (SELECT COUNT(*) FILTER (
        WHERE status NOT IN ('PENDING', 'SENDING', 'SENT', 'FAILED')
           OR attempts < 0 OR max_attempts <= 0 OR attempts > max_attempts
     ) FROM telegram_delivery_outbox) AS invalid_telegram_states,
    (SELECT COUNT(*) FILTER (
        WHERE status IN ('PENDING', 'SENDING')
          AND attempts >= max_attempts
          AND NOT (
              status NOT IN ('PENDING', 'SENDING', 'SENT', 'FAILED')
              OR attempts < 0 OR max_attempts <= 0 OR attempts > max_attempts
          )
     ) FROM telegram_delivery_outbox) AS exhausted_telegram_states,
    (SELECT COUNT(*) FILTER (WHERE status = 'SENDING' AND claimed_at IS NULL)
       FROM telegram_delivery_outbox) AS unknown_telegram_outcomes;
```

Every named repair predicate must be zero after the bridge. In particular,
recent valid `RUNNING` durable work is not stale and recent valid `SENDING`
delivery is not an unknown outcome, so neither is counted as dirty. After
Flyway, the PowerShell check above must report exactly V25.

#### V25 → V26: memory owner metadata

At V25, record these counts before and after
`pre-v26-memory-owner.sql`. The predicates validate the complete signed
`BIGINT` range lexically and compare owner text to `users.id::text`; they never
cast untrusted metadata to `BIGINT`.

```sql
SELECT
    COUNT(*) FILTER (
        WHERE memory.metadata IS NULL
           OR NOT (memory.metadata ? 'user_id')
           OR jsonb_typeof(memory.metadata -> 'user_id') IS NULL
           OR jsonb_typeof(memory.metadata -> 'user_id') NOT IN ('string', 'number')
           OR (memory.metadata ->> 'user_id') !~ '^[1-9][0-9]*$'
           OR length(memory.metadata ->> 'user_id') > 19
           OR (
               length(memory.metadata ->> 'user_id') = 19
               AND (memory.metadata ->> 'user_id') > '9223372036854775807'
           )
    ) AS unsafe_owner_metadata,
    COUNT(*) FILTER (
        WHERE memory.metadata IS NOT NULL
          AND (memory.metadata ? 'user_id')
          AND jsonb_typeof(memory.metadata -> 'user_id') IN ('string', 'number')
          AND (memory.metadata ->> 'user_id') ~ '^[1-9][0-9]*$'
          AND (
              length(memory.metadata ->> 'user_id') < 19
              OR (
                  length(memory.metadata ->> 'user_id') = 19
                  AND (memory.metadata ->> 'user_id') <= '9223372036854775807'
              )
          )
          AND NOT EXISTS (
              SELECT 1
                FROM users app_user
               WHERE app_user.id::text = memory.metadata ->> 'user_id'
          )
    ) AS orphan_owner_metadata
  FROM user_memory memory;
```

Both `unsafe_owner_metadata` and `orphan_owner_metadata` must be zero after
the bridge. After Flyway, the PowerShell check above must report exactly V26.

V23 does not reinterpret or rewrite data in its bridge. Legacy naive note
timestamps are interpreted as UTC by the immutable V23
`AT TIME ZONE 'UTC'` conversion, including values around spring and fall DST
transitions.

The V25 bridge policy is fixed: negative nullable metrics become `NULL`;
structurally meaningless FatSecret children, weight/workout readings, blank
notes, and blank Telegram text are deleted; unknown note types become
`OTHER`; invalid users receive collision-free visibly invalid placeholders;
invalid or exhausted durable jobs and deliveries become terminal with stable
non-PII codes; stale `RUNNING` jobs become `PENDING` while recent valid ones
remain `RUNNING`; and `SENDING` rows with `claimed_at IS NULL` become `FAILED`
with `LEGACY_DELIVERY_OUTCOME_UNKNOWN_PRE_V25`. V26 removes null, missing,
noncanonical, nonnumeric, nonpositive, oversized, and orphaned memory owners
before the generated `BIGINT` owner column is added; valid JSON string and
number owners remain and cascade with their user.

Do not run a bridge retroactively after its database has passed the matching
Flyway migration. A database already at or beyond V21, V23, V25, or V26 must
follow a new forward migration/incident procedure; rerunning an old bridge is
unsupported even though the scripts are idempotent at their intended boundary.

## Privacy

- Never log Telegram text, nutrition payloads, AI prompts, tokens, or raw provider responses.
- GDPR export/delete is PostgreSQL-backed and must be verified after schema migrations with the integration gate.
