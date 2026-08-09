# Keep Fast Default Gate Separate From Heavy Gates

The default development gate should stay fast enough to run after ordinary changes, while PostgreSQL integration and Spring Modulith architecture checks run through explicit Maven profiles. This keeps daily feedback quick without pretending that database and architecture checks are unnecessary.

## Considered Options

- Run every test in `mvn test`, which maximizes coverage but slows routine edits and makes local feedback noisy.
- Keep only unit tests, which is fast but loses confidence in database and module behavior.
- Split the gates: `mvn test` for fast feedback, `mvn verify -Pintegration` for PostgreSQL behavior, and `mvn test -Parchitecture` for Modulith verification.

## Consequences

New tests should be named and profiled intentionally. Do not hide slow PostgreSQL or architecture checks in the default gate, and do not claim a database or module-boundary change is complete without running its dedicated gate.
