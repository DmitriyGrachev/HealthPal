# FitnessApp — AI Agent Context

## Project Overview
Spring Boot 3.4.2 fitness tracker with AI insights.
Java 21, Spring Modulith, PostgreSQL + pgvector, Spring AI 1.1.2.

## Architecture: Hexagonal + Spring Modulith
Modules communicate ONLY via Spring events (@ApplicationModuleListener).
NO direct dependency injection between modules.
Each module has: adapter/in, adapter/out, application/port/in, application/port/out, domain.

## Active Modules
| Module      | Responsibility                        | Publishes                    | Listens to                        |
|-------------|---------------------------------------|------------------------------|-----------------------------------|
| ai          | Generate insights via LLM             | InsightGeneratedEvent        | NutritionSyncedEvent, Weekly/Monthly |
| memory      | Vector storage (pgvector RAG)         | -                            | InsightGeneratedEvent, UserNoteCreatedEvent |
| nutrition   | FatSecret sync, food tracking         | NutritionSyncedEvent         | -                                 |
| analytics   | Weekly/Monthly report orchestration   | WeeklyReportRequestedEvent   | -                                 |
| auth        | Users, notes, JWT                     | UserNoteCreatedEvent         | -                                 |
| workout     | Exercise tracking                     | WorkoutSavedEvent            | -                                 |

## Memory System (CRITICAL — read carefully)
Vector store: pgvector table `user_memory`
Embedding model: nvidia/llama-nemotron-embed-vl-1b-v2:free via OpenRouter

Memory types:
- FACT: permanent (allergies, goals) — no TTL
- SEMANTIC: AI patterns — TTL depends on insight type
- EPISODIC: user notes/events — TTL 7-14 days

Memory horizons stored in metadata:
- LONG_TERM: permanent facts, monthly insights
- MID_TERM: weekly insights (TTL 90 days)
- SHORT_TERM: daily insights (TTL 14 days), temporary notes (TTL 7 days)

Key field in VectorStore metadata: "user_id" (Long)
Filter in queries: FilterExpressionBuilder().eq("user_id", userId)

## AI Stack
- Primary: Google Gemini 2.5 Flash (via spring-ai-google-genai)
- Fallback: OpenRouter models (via spring-ai-openai with base-url override)
- Structured output: BeanOutputConverter<NutritionInsightResponse>
- All AI responses typed as NutritionInsightResponse record

## Key Response Type
com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse
Contains: summary, telegramSummary, macroAnalysis, anomalies,
recommendations, followUpQuestions, goalAlignment, confidenceScore

## Database
PostgreSQL 16, Flyway migrations in src/main/resources/db/migration/
JPA entities: auth, nutrition, workout, ai modules
pgvector: user_memory table (no JPA, managed by Spring AI)

## Rules for AI Agents (MUST FOLLOW)
1. Modules NEVER inject beans from other modules directly
2. Cross-module communication = Spring ApplicationEventPublisher only
3. @ApplicationModuleListener for async event handling
4. New module needs package-info.java with @ApplicationModule
5. All new DB changes = new Flyway migration file
6. Never use spring.main.allow-bean-definition-overriding=true
7. VectorStore bean qualifier: @Qualifier not needed (only one VectorStore bean)
8. UserRepository is in auth.adapter.out.persistence.repository

## What We're Building Next
Telegram Bot module (Phase 2).
See TELEGRAM_PLAN.md for detailed spec.
Bot communicates with other modules ONLY via events.
New tables: telegram_users, conversation_history, conversation_state