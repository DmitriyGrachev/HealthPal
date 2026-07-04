# FitnessApp

FitnessApp is a Java 21 Spring Boot modular monolith for auth, nutrition, workout analytics, AI insights, memory, and Telegram workflows. The project uses Maven, PostgreSQL with pgvector, Flyway migrations, Spring Security JWT auth, Spring AI provider adapters, FatSecret OAuth, and a Telegram bot integration.

## Requirements

- Java 21
- Maven 3.9+
- Docker with Docker Compose
- PostgreSQL-compatible client tools are optional but useful for inspecting the local database

## Local Database

Start the local PostgreSQL plus pgvector service:

```bash
docker compose up -d db
```

The committed `docker-compose.yml` starts a `pgvector/pgvector:pg16` database on port `5432`. Keep schema changes in new Flyway migrations under `src/main/resources/db/migration`; existing migrations are immutable.

## Environment

The main `src/main/resources/application.properties` is production-like and reads secrets from environment variables. Set these through your shell, IDE run configuration, or local secret manager before running the app:

```dotenv
DB_URL=<jdbc-postgresql-url>
DB_USERNAME=<database-user>
DB_PASSWORD=<database-password>
TELEGRAM_BOT_TOKEN=<telegram-bot-token>
FITNESS_APP_SECRET=<base64-jwt-signing-secret>
FATSECRET_CLIENT_ID=<fatsecret-client-id>
FATSECRET_CLIENT_SECRET=<fatsecret-client-secret>
FATSECRET_CONSUMER_KEY=<fatsecret-consumer-key>
FATSECRET_CONSUMER_SECRET=<fatsecret-consumer-secret>
FATSECRET_REDIRECT_URI=<fatsecret-redirect-uri>
FATSECRET_CALLBACK_URL=<fatsecret-callback-url>
FATSECRET_TOKEN_ENCRYPTION_KEY=<base64-aes-key>
OPENROUTER_API_KEY=<openrouter-api-key>
GEMINI_API_KEY=<gemini-api-key>
```

Optional environment variables:

```dotenv
TELEGRAM_BOT_USERNAME=<telegram-bot-username>
FITNESS_APP_JWT_EXPIRATION=PT24H
FITNESS_APP_CORS_ALLOWED_ORIGINS=http://localhost:3000
FITNESS_APP_AUTH_RATE_LIMIT_CAPACITY=5
FITNESS_APP_AUTH_RATE_LIMIT_REFILL_PERIOD=PT1M
MEMORY_CLEANUP_ENABLED=true
MEMORY_CLEANUP_CRON=0 0 4 * * *
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
```

`.env` is ignored for local convenience and must not be committed. Do not put real Telegram tokens, JWT secrets, FatSecret credentials, AI provider keys, OAuth tokens, or database passwords in tracked files.

## Run

After the database is up and required environment variables are set:

```bash
mvn spring-boot:run
```

Useful local checks:

- Health: `GET /actuator/health`
- Workout summary: `GET /api/v1/workout-analytic/summary`
- OpenAPI JSON: `GET /v3/api-docs` when `SPRINGDOC_API_DOCS_ENABLED=true`
- Swagger UI: `/swagger-ui.html` when `SPRINGDOC_SWAGGER_UI_ENABLED=true`

OpenAPI and Swagger UI are disabled by default and protected by `ADMIN` when enabled.

## Tests

Fast default gate:

```bash
mvn test
```

Spring Modulith architecture gate:

```bash
mvn test -Parchitecture
```

Equivalent Maven profile ordering:

```bash
mvn -Parchitecture test
```

PostgreSQL/Testcontainers integration gate, when Docker is available:

```bash
mvn verify -Pintegration
```

Dependency hygiene check:

```bash
mvn dependency:analyze
```

Tests must not call real OpenRouter, Gemini, Telegram, or FatSecret services. Use mocks for external provider behavior.

## Profiles And Local Safety

- Default runtime config does not activate a Spring profile.
- `test` uses `src/test/resources/application-test.properties` and test-safe fake values.
- Enable `dev` explicitly only for local development workflows that need dev-only beans or endpoints.
- Dev/test operational endpoints such as `/test/**` are admin-only.

## Secret Hygiene

- Keep `.env`, local `application-dev.properties`, key stores, and generated logs out of Git.
- Do not log raw Telegram messages, nutrition details, prompts with user data, OAuth tokens, JWTs, API keys, FatSecret tokens, or database credentials.
- Prefer metadata in logs: user id, chat id, command type, provider, status, exception class, and stable error code.
