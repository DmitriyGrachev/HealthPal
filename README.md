🏋️‍♂️ HealthPal (Fitness AI)
HealthPal is an advanced fitness application backend that goes beyond basic workout and nutrition tracking. It leverages Generative AI to discover non-obvious correlations and deliver deep, personalized insights.

The project is built using modern Enterprise development patterns: Modular Monolith (Spring Modulith), Hexagonal Architecture (Ports & Adapters), CQRS, and Transactional Outbox.

🏗 Application Architecture
The project is designed to withstand high loads and scale easily. Business logic is strictly divided into isolated domains (modules).

Key Architectural Patterns:
Spring Modulith: Strict module isolation. Modules communicate with each other exclusively through public interfaces (APIs) located in root packages or via events.

Hexagonal Architecture: Each module is structured with domain, application (containing port.in and port.out), adapter.in (REST controllers), and adapter.out (Persistence, external APIs) layers.

Event-Driven & Transactional Outbox: When saving data (e.g., nutrition synchronization), modules do not call the AI directly. Instead, they publish events. Spring Modulith automatically persists these events in a database table within the same transaction, guaranteeing 100% event delivery to the AI module, even in the event of a server crash.

CQRS (Basic Implementation): Write operations are handled via Spring Data JPA (Hibernate) to maintain domain integrity, while heavy analytics (weekly/monthly statistics gathering) are implemented using raw SQL via JdbcTemplate for maximum performance.

📦 Module Structure (Current Features)

🔐 auth Module
Handles user registration, authentication, and JWT token issuance.
Provides a public API (CurrentUserApi, UserApi) for other modules.

🍏 nutrition Module
Integration with the FatSecret API (OAuth 1.0a).
Fetches nutrition logs, parses food items, and calculates daily macronutrients (protein, fats, carbs).
Publishes the NutritionSyncedEvent.

🏋️‍♂️ workout Module
Imports workout data from third-party apps (parsing CSV files from Jefit).
Stores workout sessions, exercises, sets, and weights.

📊 analytics Module (Orchestrator)
Utilizes scheduled cron jobs for data aggregation.
Retrieves data from the nutrition and workout modules (via their public Read-APIs) once a week to generate an aggregated WeeklyReportRequestedEvent.

🧠 ai Module
Acts as an asynchronous event consumer, listening for nutrition updates and weekly reports.
Utilizes Spring AI (integrated with Google Gemini / LLMs) to analyze correlations (e.g., "Your bench press is stagnating due to a systematic lack of carbohydrates on training days").
Persists AI-generated insights in PostgreSQL, using the JSONB data type for metadata storage.

🛠 Technology Stack
Core: Java 17, Spring Boot 3.4.2
Architecture: Spring Modulith, Hexagonal Architecture
Database: PostgreSQL 16, Flyway (Migrations)
AI Integration: Spring AI (Google GenAI / OpenAI)
Security: Spring Security, JWT
External APIs: ScribeJava (OAuth 1.0a for FatSecret)
Utilities: Lombok, MapStruct (planned), Caffeine Cache

🚀 Roadmap (Future Plans)
The project is under active development. The following features are planned:

🤖 Advanced AI & Optimization
Smart Model Fallback: Protection against 429 Too Many Requests limits. Implementing a pool of several LLM providers with automatic fallback if the primary neural network is unavailable.
Token Optimization: SQL-level data aggregation before sending requests to the LLM (e.g., sending only the Top-5 consumed foods instead of the entire list) to reduce API costs.
Science-Based RAG: Integration of a vector database (pgvector). Ingesting scientific fitness articles and podcasts to generate scientifically backed insights.
Monthly Pattern Recognition: Meta-analysis of weekly insights to identify long-term progress trends.

🧑‍⚕️ User Context
User Notes (State Journal): Ability to input context (injuries, vacations, stress levels) so the AI can adjust its recommendations accordingly.
Profile Synchronization: Fetching dynamic TDEE and user goals (weight loss/muscle gain) directly from FatSecret.
Step Tracking Integration (NEAT): Ingesting data from Apple Health/Google Fit to evaluate daily non-exercise activity thermogenesis.

⚙️ Infrastructure & DevOps
Caching: Implementing Caffeine/Redis to optimize the delivery of charts and dashboards to the frontend.
Docker & CI/CD: Containerizing the application (docker-compose.yml including the database and services) and configuring GitHub Actions.
Developed with a focus on Clean Architecture and meaningful AI integrations.
