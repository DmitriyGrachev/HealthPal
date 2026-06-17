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


## graphify

This project has a graphify knowledge graph at .graphify/.

Rules:
- For codebase or architecture questions, when `.graphify/graph.json` exists, first run `graphify query "<question>"` (or `graphify path "<A>" "<B>"` / `graphify explain "<concept>"`); these return a scoped subgraph, usually much smaller than `GRAPH_REPORT.md` or raw grep output
- If .graphify/wiki/index.md exists, navigate it instead of reading raw files
- If .graphify/graph.json is missing but graphify-out/graph.json exists, run `graphify migrate-state --dry-run` first; if tracked legacy artifacts are reported, ask before using the recommended `git mv -f graphify-out .graphify` and commit message
- If .graphify/needs_update exists or .graphify/branch.json has stale=true, warn before relying on semantic results and run /graphify . --update when appropriate
- In Gemini CLI, the reliable explicit custom command is `/graphify ...`
- If the user asks to build, update, query, path, or explain the graph, use the installed `/graphify` custom command or the configured `graphify` MCP server instead of ad-hoc file traversal
- Before proposing or committing .graphify artifacts, run `graphify portable-check .graphify`; commit-safe graph artifacts must use repo-relative paths, and never commit .graphify/branch.json, .graphify/worktree.json, .graphify/needs_update, or .graphify/cache/. If a repo already tracks any of them, first add them to .gitignore, then propose `git rm --cached .graphify/branch.json .graphify/worktree.json .graphify/needs_update` and `git rm -r --cached .graphify/cache`; never mutate git state without asking
- Before deep graph traversal, prefer `graphify summary --graph .graphify/graph.json` or MCP `first_hop_summary` for compact first-hop orientation
- For review impact on changed files, use `graphify review-delta --graph .graphify/graph.json` or MCP `review_delta` instead of generic traversal
- Read `.graphify/GRAPH_REPORT.md` only for broad architecture review or when `query` / `path` / `explain` do not surface enough context
- After modifying code files in this session, run `npx graphify hook-rebuild` to keep the graph current
