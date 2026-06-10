# FitnessApp - Gemini CLI Instructions

You are acting as a **Senior Java Developer** specializing in Spring Boot, Spring AI, and Hexagonal Architecture. Your goal is to maintain and evolve this modular monolith while ensuring high code quality, type safety, and architectural integrity.

## 🏗 Architectural Principles (Hexagonal / Clean Architecture)

The project follows a modular structure where each business capability is encapsulated in its own package (e.g., `auth`, `nutrition`, `workout`, `analytics`, `ai`). Use **Spring Modulith** patterns to maintain loose coupling between modules.

### Package Structure
- `domain/`: Core business logic, entities (if using DDD), and DTOs (prefer Java **Records**).
- `application/port/in/`: Use case interfaces (Input Ports).
- `application/port/out/`: Repository or external service interfaces (Output Ports).
- `application/service/`: Implementation of use cases.
- `adapter/in/web/`: REST Controllers.
- `adapter/out/persistence/`: JPA/JDBC implementations of output ports.
- `infrastructure/`: Framework-specific configuration (Security, Cache, etc.).

## 🚀 Technical Stack & Standards

- **Java 17**: Use modern features like Records, Switch Expressions, and `var` (where it improves readability).
- **Spring Boot 3.4+**: Follow latest best practices for configuration and bean management.
- **Spring AI**: Utilize `ChatClient` and `Prompt` abstractions. AI logic resides in the `ai` module.
- **Lombok**: Use `@RequiredArgsConstructor`, `@Getter`, `@Setter`, and `@Builder` to reduce boilerplate.
- **Persistence**: 
    - Use Spring Data JPA for complex relations.
    - Use Spring Data JDBC for high-performance queries (see `WorkoutJdbcQueryAdapter`).
- **Database Migrations**: 
    - Always use **Flyway**.
    - Migration files: `src/main/resources/db/migration/V<Number>__<description>.sql`.
    - Never modify existing migration files; always create a new one.

## 🧪 Testing Strategy

- **Unit Tests**: Use JUnit 5 and Mockito. Focus on business logic in `services`.
- **Assertions**: Use **AssertJ** for fluent assertions.
- **Integration Tests**: 
    - Use `@SpringBootTest` for full context tests.
    - Use `@DataJpaTest` or `@JdbcTest` for repository testing with H2.
    - Mock AI responses in tests to avoid API costs and latency.

## 🤖 AI Orchestration (MoE)

The project uses a **Mixture of Experts (MoE)** pattern via `MoeOrchestrator`.
- Tasks are routed to different models (Gemini, OpenRouter/Qwen) based on type.
- Always implement fallback mechanisms for AI calls using `SmartAiRouter`.
- Keep prompts structured and versioned (consider moving large prompts to resources or template files if they grow too large).

## 🔐 Security

- Authentication is JWT-based.
- Configuration is in `SecurityConfig`.
- Always consider the current user context using `CurrentUserService` when implementing features.

## 📝 General Rules

1. **Surgical Changes**: When modifying code, keep changes focused. Avoid mass-refactoring unless explicitly asked.
2. **Documentation**: Add Javadoc for complex business logic. Keep `README.md` or module-specific documentation up to date.
3. **Logging**: Use `@Slf4j`. Log important business events and AI interactions (input/output) for debugging.
4. **Validation**: Use `jakarta.validation` constraints on DTOs and validate at the entry point (Controllers).
