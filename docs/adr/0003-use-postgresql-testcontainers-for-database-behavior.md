# Use PostgreSQL Testcontainers For Database Behavior

FitnessApp uses PostgreSQL-specific behavior through Flyway, JDBC queries, JSONB, and pgvector. Database behavior should be proven against PostgreSQL with Testcontainers, while H2 may only remain for fast smoke tests that do not claim PostgreSQL, Flyway, SQL, or pgvector correctness.

## Considered Options

- Use H2 for most persistence tests, which is fast but can hide PostgreSQL-specific failures.
- Run tests against a shared local database, which is realistic but brittle and machine-dependent.
- Use PostgreSQL Testcontainers for persistence checks, which costs startup time but gives repeatable production-like behavior.

## Consequences

Name PostgreSQL-backed tests `*IntegrationTest.java` and run them with `mvn verify -Pintegration`. Do not edit old Flyway migrations to make H2 happy; add a new migration or adjust the test boundary.
