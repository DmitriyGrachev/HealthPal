# ADR 005: Unified Time Policy and Timezone Resolution

## Status
Accepted

## Context
Prior implementation contained hardcoded timezone strings (`Europe/Kiev`), server-local `now()` calls, and inconsistent conversions between UTC `Instant`, `LocalDateTime`, and `TIMESTAMP`.

## Decision
1. All absolute system timestamps MUST be recorded as UTC (`Instant` / `TIMESTAMP WITH TIME ZONE`).
2. Global `Clock` bean is configured to `Clock.systemUTC()` via `AppTimeConfig`.
3. User local representations (`LocalDate`, `LocalDateTime`) are rendered based on user IANA timezone via `UserTimeService`.
4. Hardcoded local timezone strings (e.g. `Europe/Kiev`) are removed from parser and service logic.

## Consequences
- Guarantees predictable DST round-trip handling.
- Prevents server-location dependent timing bugs in multi-region deployments.
