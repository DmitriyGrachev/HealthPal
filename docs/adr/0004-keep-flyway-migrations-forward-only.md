# Keep Flyway Migrations Forward-Only

Committed Flyway migrations are part of the database contract. When schema, pgvector indexes, constraints, or table shapes need to change, add a new migration instead of rewriting an existing one.

## Considered Options

- Rewrite old migrations until the current local database looks right, which is convenient during development but unsafe for any database that has already run them.
- Add forward-only migrations, which can leave a messier migration history but preserves upgradeability.

## Consequences

Fixes such as restoring pgvector indexes, changing vector dimensions, adding foreign keys, or encrypting persisted tokens should create new `V*__*.sql` files. Tests should verify the end state after all migrations, not the tidiness of an individual historical migration.

## Historical Upgrade Bridges

V1 through V28 are immutable. A database that is stopped immediately before a
known historical boundary uses the matching operational bridge, in a single
verified transaction, before allowing Flyway to continue:

- at V20, run `pre-v21-durable-job-idempotency.sql` before V21;
- at V22, run `pre-v23-absolute-timestamps.sql` before V23;
- at V24, run `pre-v25-domain-invariants.sql` before V25;
- at V25, run `pre-v26-memory-owner.sql` before V26.

These scripts live outside Flyway discovery and are never recorded as Flyway
migrations. They take table locks, contain no transaction-control statements,
do not log row content, and are repeatable. The database must have a verified
backup, writers must be shut down, and aggregate-only preflight and
postflight evidence must be retained. A failed bridge transaction is rolled
back and the database is restored or corrected before retrying; `flyway repair`
is not a rollback mechanism and is not used.

The V23 bridge intentionally performs no timestamp rewrite. V23's existing
`AT TIME ZONE 'UTC'` expression is the conversion authority for naive legacy
note timestamps. The V25 bridge turns invalid nullable metrics into `NULL`,
deletes structurally meaningless child/reading rows and blank Telegram text,
maps unknown note types to `OTHER`, gives invalid users collision-free visibly
invalid placeholders, and terminalizes invalid or exhausted durable work with
stable non-PII error codes. Stale `RUNNING` work becomes `PENDING`, recent
valid `RUNNING` work remains running, and `SENDING` with no claim timestamp is
terminalized as an unknown delivery outcome. The V26 bridge deletes unsafe
memory metadata before any `BIGINT` cast and retains only canonical positive,
in-range owners that can be resolved to a user.

Bridges are boundary procedures, not general cleanup jobs. They must never be
retroactively run after the database has passed the corresponding Flyway
migration. A later defect is fixed with a new forward migration or a newly
documented boundary procedure for databases that have not crossed that
boundary.
