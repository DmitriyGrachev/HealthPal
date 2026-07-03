# Keep Cross-Module Events In Stable API Packages

FitnessApp is a Spring Modulith modular monolith, so modules should communicate through stable public contracts rather than importing another module's internal implementation classes. Cross-module events such as Telegram requests, generated insights, nutrition syncs, and report requests should live in an exposed API package or another clearly public package owned by the publishing module.

## Considered Options

- Direct service calls between modules, which are simple but make module cycles easy to create.
- Events declared in private application or adapter packages, which keep files near producers but make consumers depend on internals.
- Stable public event contracts, which add a little ceremony but preserve module locality and keep Spring Modulith verification meaningful.

## Consequences

When adding a new cross-module workflow, create or move the event contract to a public package first, then let producers publish and consumers listen from their own modules. If an event is only internal to one module, keep it internal and do not promote it prematurely.
