# Isolate External Providers Behind Ports

AI providers, FatSecret, Telegram, and persistence details should stay behind module ports and adapters. Application services may depend on the port contracts, but provider-specific clients, exceptions, request syntax, credentials, and retry behavior belong inside the owning adapter or router.

## Considered Options

- Let use cases call provider SDKs directly, which is quick but spreads provider knowledge and makes tests brittle.
- Keep provider behavior behind ports, which adds interfaces but concentrates change and failure handling.

## Consequences

OpenRouter and Gemini belong behind `AiModelPort` and routing abstractions. FatSecret belongs behind nutrition ports. Telegram command handling should publish stable events or call Telegram-owned services rather than leaking bot SDK details into other modules.
