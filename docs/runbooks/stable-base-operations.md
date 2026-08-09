# Stable Base Operations Runbook

## Startup

1. Provide the production variables documented in `src/main/resources/application.properties`.
2. Start the application with the default profile. Flyway must migrate a clean PostgreSQL database before the application accepts traffic.
3. For local development use `SPRING_PROFILES_ACTIVE=dev`; the dev profile must point to PostgreSQL and keep `spring.jpa.hibernate.ddl-auto=validate`.
4. Verify `GET /actuator/health` before enabling scheduled workers.

## Scheduled Work

- Nutrition sync runs in UTC and uses `nutrition.sync.today.cron`, `nutrition.sync.window-days`, and `nutrition.sync.batch-size`.
- Each user sync creates a durable job with an idempotency key. A second scheduler instance may create the same key, but only one job can be claimed.
- In one JVM an overlapping scheduler invocation is skipped. A failed user does not stop the remaining batch.
- Durable job and Telegram outbox workers claim rows with bounded retries. `SENDING`/`RUNNING` rows are recovered after their claim timeout.

## Recovery

- Inspect `durable_jobs` for `FAILED` rows and retry them through `POST /api/v1/jobs/{id}/retry` as the owning user/operator.
- Inspect `telegram_delivery_outbox` for `FAILED` rows and provider error codes. `429` and `5xx` remain retryable; malformed requests are terminal.
- If a deployment stops during a claim, wait for the recovery window before manually changing status. Preserve the original `error_message` when opening an incident.

## Observability

- Actuator metrics expose only aggregate operational gauges: `fitnessapp.durable_jobs.pending`, `fitnessapp.durable_jobs.failed`, `fitnessapp.telegram_outbox.pending`, `fitnessapp.telegram_outbox.failed`, and `fitnessapp.event_publication.incomplete`.
- Alert on sustained growth of failed or incomplete gauges. Never add message text, prompts, tokens, payloads, or user identifiers as metric tags.

## Shutdown and Deployment

- Stop accepting new traffic, then allow the application to finish the current scheduler/worker tick.
- Spring waits up to 30 seconds for scheduled and async executors. The AI executor waits five seconds for in-flight provider work, then interrupts it; the durable job or outbox state remains the recovery source for the next worker cycle.
- Do not run destructive SQL against `durable_jobs`, `telegram_delivery_outbox`, `event_publication`, or `user_memory` during deployment.
- Flyway migrations are append-only. Roll back by deploying a previous application version only when the new migration is backward compatible; otherwise restore the database backup and follow the incident procedure.

## Privacy

- Never log Telegram text, nutrition payloads, AI prompts, tokens, or raw provider responses.
- GDPR export/delete is PostgreSQL-backed and must be verified after schema migrations with the integration gate.
