# Runtime Configuration

The main `application.properties` is production-like and must not activate a Spring profile by default.
Local development must opt in explicitly:

```bash
SPRING_PROFILES_ACTIVE=dev
```

## Required Environment Variables

Set these for production-like runs:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `TELEGRAM_BOT_TOKEN`
- `FITNESS_APP_SECRET` for the `fitness.app.secret` property
- `FATSECRET_CLIENT_ID`
- `FATSECRET_CLIENT_SECRET`
- `FATSECRET_CONSUMER_KEY`
- `FATSECRET_CONSUMER_SECRET`
- `FATSECRET_REDIRECT_URI`
- `FATSECRET_CALLBACK_URL`
- `OPENROUTER_API_KEY`
- `GEMINI_API_KEY`

Optional:

- `TELEGRAM_BOT_USERNAME`

Tests use `src/test/resources/application-test.properties` through the `test` Spring profile and should not require local `dev` settings.
