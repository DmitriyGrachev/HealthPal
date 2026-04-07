🏋️‍♂️ HealthPal (Fitness AI)
HealthPal is an advanced backend for a fitness application that doesn’t just collect workout and nutrition statistics — it leverages Generative AI to uncover non-obvious correlations and deliver deep, personalized insights.
The project is built using modern Enterprise development patterns: Modular Monolith (Spring Modulith), Hexagonal Architecture (Ports & Adapters), CQRS, and Transactional Outbox.

🏗 Application Architecture
The project is designed to handle high loads and scale effortlessly. Business logic is strictly separated into isolated domains (modules).
Key Architectural Patterns:

Spring Modulith: Strict module isolation. Modules communicate with each other only through public interfaces (APIs) located in root packages or via events.
Hexagonal Architecture: Each module is divided into domain, application (with port.in and port.out), adapter.in (REST controllers), and adapter.out (Persistence and external APIs) layers.
Event-Driven & Transactional Outbox: When saving data (e.g., nutrition sync), modules do not call AI directly. Instead, they publish events. Spring Modulith automatically stores these events in a database table within the same transaction, guaranteeing 100% event delivery to the AI module even if servers crash.
CQRS-lite: Write operations use Spring Data JPA (Hibernate) to preserve domain integrity, while heavy analytics (weekly/monthly statistics) are implemented with raw SQL via JdbcTemplate for maximum performance.


📦 Module Structure (Current Functionality)

🔐 auth Module
Handles registration, authentication, and JWT token issuance.
Provides a public API (CurrentUserApi, UserApi) for other modules.

🍏 nutrition Module
Integration with FatSecret API (OAuth 1.0a).
Pulls nutrition logs, parses products, and calculates daily macronutrients (proteins, fats, carbs).
Publishes NutritionSyncedEvent.

🏋️‍♂️ workout Module
Imports workout data from third-party apps (CSV parsing from Jefit).
Stores training sessions, exercises, sets, and weights.

📊 analytics Module (Orchestrator)
Cron jobs for analytics collection.
Once a week, it gathers data from the nutrition and workout modules (via their public Read APIs) and creates an aggregated WeeklyReportRequestedEvent.

🧠 ai Module
Asynchronous event consumer. Listens for nutrition updates and weekly reports.
Uses Spring AI (integration with Google Gemini / LLMs) to analyze correlations (e.g.: "Your bench press is stagnating due to consistently low carbohydrate intake on training days").
Saves AI insights to PostgreSQL using the JSONB data type for metadata.



🛠 Technology Stack

Core: Java 17, Spring Boot 3.4.2
Architecture: Spring Modulith, Hexagonal Architecture
Database: PostgreSQL 16, Flyway (migrations)
AI Integration: Spring AI (Google GenAI / OpenAI)
Security: Spring Security, JWT
External APIs: ScribeJava (OAuth 1.0a for FatSecret)
Utilities: Lombok, MapStruct (planned), Caffeine Cache


🚀 Roadmap (Future Plans)
The project is actively evolving. The following features are planned:
🤖 Advanced AI & Optimization

Smart Model Fallback: Protection against 429 Rate Limit. A pool of multiple LLM providers with automatic switching if the primary model is unavailable.
Token Optimization: SQL-level data aggregation before sending to the LLM (sending only the Top-5 products instead of the full list) to reduce request costs.
Science-Based RAG: Integration of a vector database (pgvector). Loading scientific articles and fitness podcasts to generate insights with scientific backing.
Monthly Pattern Recognition: Meta-analysis of weekly insights to detect long-term progress trends.

🧑‍⚕️ User Context

User Notes (State Journal): Ability to add context (injuries, vacation, stress) so the AI can adjust its recommendations accordingly.
Profile Sync: Pulling dynamic TDEE and goals (weight loss / bulking) from FatSecret.
Step Integration (NEAT): Receiving data from Apple Health/Google Fit to assess daily activity levels.

⚙️ Infrastructure & DevOps

Caching: Implementing Caffeine/Redis to optimize chart and dashboard delivery to the frontend.
Docker & CI/CD: Packaging the application into containers (docker-compose.yml with DB and services) and setting up GitHub Actions.


Developed with focus on Clean Architecture and meaningful AI integrations.
