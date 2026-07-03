# Do Not Call Real External Providers In Tests

Tests must not call real OpenRouter, Gemini, FatSecret, Telegram, or other external services. FitnessApp tests should prove local behavior with mocks, fakes, fixtures, or Testcontainers-controlled infrastructure.

## Considered Options

- Call real providers in tests to gain confidence in integration behavior, which is tempting but slow, flaky, costly, and unsafe for secrets or user data.
- Mock provider ports and reserve real-provider checks for explicit manual verification or dedicated non-default environments.

## Consequences

Unit and web tests should mock provider ports and assert stable behavior: status, error code, provider call count, event publication, and privacy-safe logging. Provider credentials should never be required for `mvn test`.
