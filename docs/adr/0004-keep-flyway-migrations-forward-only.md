# Keep Flyway Migrations Forward-Only

Committed Flyway migrations are part of the database contract. When schema, pgvector indexes, constraints, or table shapes need to change, add a new migration instead of rewriting an existing one.

## Considered Options

- Rewrite old migrations until the current local database looks right, which is convenient during development but unsafe for any database that has already run them.
- Add forward-only migrations, which can leave a messier migration history but preserves upgradeability.

## Consequences

Fixes such as restoring pgvector indexes, changing vector dimensions, adding foreign keys, or encrypting persisted tokens should create new `V*__*.sql` files. Tests should verify the end state after all migrations, not the tidiness of an individual historical migration.
