# Graph Report - .  (2026-06-12)

## Corpus Check
- Corpus is ~25 794 words - fits in a single context window. You may not need a graph.

## Summary
- 764 nodes · 802 edges · 100 communities detected
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output
- Edge kinds: method: 343 · contains: 179 · imports: 125 · calls: 68 · implements: 43 · inherits: 35 · references: 9


## Input Scope
- Requested: auto
- Resolved: committed (source: default-auto)
- Included files: 207 · Candidates: 232
- Excluded: 363 untracked · 373 ignored · 20 sensitive · 0 missing committed
- Recommendation: Use --scope all or graphify.yaml inputs.corpus for a knowledge-base folder.

## Graph Freshness
- Built from Git commit: `d7f01f1`
- Compare this hash to `git rev-parse HEAD` before trusting freshness-sensitive graph output.
## God Nodes (most connected - your core abstractions)
1. `FitnessAiService` - 14 edges
2. `UserRepository` - 11 edges
3. `NutritionPersistenceAdapter` - 11 edges
4. `WorkoutJdbcQueryAdapter` - 11 edges
5. `UserDetailsService` - 9 edges
6. `NutritionJdbcQueryAdapter` - 9 edges
7. `JefitCsvParserAdapter` - 9 edges
8. `User` - 8 edges
9. `NutritionController` - 8 edges
10. `NoteCommandHandler` - 8 edges

## Surprising Connections (you probably didn't know these)
- `AiInsightRepository` --inherits--> `JpaRepository`  [EXTRACTED]
  src/main/java/com/fit/fitnessapp/ai/AiInsightRepository.java →   _Bridges community 22 → community 20_
- `AiInsightRepository` --inherits--> `Long`  [EXTRACTED]
  src/main/java/com/fit/fitnessapp/ai/AiInsightRepository.java →   _Bridges community 22 → community 40_
- `NoteCommandHandler` --implements--> `CommandHandler`  [EXTRACTED]
  src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/NoteCommandHandler.java →   _Bridges community 9 → community 1_
- `ProfileJpaRepositoryImpl` --inherits--> `JpaRepository`  [EXTRACTED]
  src/main/java/com/fit/fitnessapp/nutrition/adapter/out/persistence/ProfileJpaRepositoryImpl.java →   _Bridges community 40 → community 20_
- `UserNoteJpaRepository` --inherits--> `JpaRepository`  [EXTRACTED]
  src/main/java/com/fit/fitnessapp/auth/adapter/out/persistence/UserNoteJpaRepository.java →   _Bridges community 31 → community 20_

## Communities

### Community 0 - "Community 0"
Cohesion: 0.06
Nodes (17): MonthlyReportOrchestratorTest, MonthlyReportOrchestrator, MonthlyReportTransactionService, WeeklyReportOrchestrator, WeeklyReportTransactionService, NutritionMonthlyApi, NutritionQueryUseCase, NutritionWeeklyApi (+9 more)

### Community 1 - "Community 1"
Cohesion: 0.05
Nodes (12): CommandHandler, AskCommandHandler, LinkCommandHandler, StartCommandHandler, TestGenerateHandler, TodayCommandHandler, WeightCommandHandler, TelegramNotificationListener (+4 more)

### Community 2 - "Community 2"
Cohesion: 0.07
Nodes (13): core, LoginUseCase, org, UserAuthenticationPort, UserAuthenticationAdapter, security, LoginService, UserDetailsService (+5 more)

### Community 3 - "Community 3"
Cohesion: 0.07
Nodes (8): UserNoteControllerTest, UserNoteControllerValidationTest, TelegramNoteListener, UserNotePersistenceAdapter, UserNoteService, UserNotePersistencePort, UserNoteUseCase, UserNoteController

### Community 4 - "Community 4"
Cohesion: 0.09
Nodes (6): MoeOrchestrator, SmartAiRouter, SmartAiRouterTest, AiModelPort, GeminiAdapter, OpenRouterAdapter

### Community 5 - "Community 5"
Cohesion: 0.09
Nodes (6): ConnectFatSecretUseCase, NutritionCommandPort, NutritionPersistenceAdapter, NutritionService, SyncNutritionUseCase, NutritionController

### Community 6 - "Community 6"
Cohesion: 0.18
Nodes (3): FitnessAiService, ProfileJpaRepository, ProfileUseCase

### Community 7 - "Community 7"
Cohesion: 0.14
Nodes (7): ImportWorkoutUseCase, JefitCsvParserAdapter, TempExercise, TempWorkout, WorkoutImportService, WorkoutImportController, WorkoutParserPort

### Community 8 - "Community 8"
Cohesion: 0.12
Nodes (6): AiController, RateLimitInterceptor, RateLimitInterceptorTest, CurrentUserApi, HandlerInterceptor, CurrentUserService

### Community 9 - "Community 9"
Cohesion: 0.14
Nodes (4): ConversationStateUseCase, NoteCommandHandler, NoteCommandHandlerTest, ConversationStateService

### Community 10 - "Community 10"
Cohesion: 0.13
Nodes (4): AuthControllerTest, RegisterUserPort, RegisterService, AuthController

### Community 11 - "Community 11"
Cohesion: 0.15
Nodes (3): UserAuthorityTest, UserRepository, User

### Community 12 - "Community 12"
Cohesion: 0.21
Nodes (5): DailyInsight, MoeOrchestratorTest, MonthlyReport, QuickAnalysis, WeeklyReport

### Community 13 - "Community 13"
Cohesion: 0.15
Nodes (5): AiAuthException, AiInvalidRequestException, AiUnavailableException, UserAlreadyExistsException, RuntimeException

### Community 14 - "Community 14"
Cohesion: 0.22
Nodes (3): TelegramWeightListener, WeightHistoryRepository, WeightHistoryUseCase

### Community 15 - "Community 15"
Cohesion: 0.29
Nodes (10): event_publication, fatsecret_connection, fatsecret_day, fatsecret_food, profile, user_roles, users, workout (+2 more)

### Community 16 - "Community 16"
Cohesion: 0.22
Nodes (3): UserAdapter, UserPort, TestController

### Community 17 - "Community 17"
Cohesion: 0.39
Nodes (1): NutritionMonthlyApiTest

### Community 18 - "Community 18"
Cohesion: 0.25
Nodes (1): CodeHygieneTest

### Community 19 - "Community 19"
Cohesion: 0.32
Nodes (4): WorkoutPersistenceAdapter, WorkoutSetJpaRepository, WorkoutPersistencePort, WorkoutSetJpaEntity

### Community 20 - "Community 20"
Cohesion: 0.38
Nodes (4): UserRepositorySignatureTest, ConversationStateEntity, JpaRepository, ConversationStateRepository

### Community 21 - "Community 21"
Cohesion: 0.33
Nodes (2): MemoryQueryUseCase, MemoryService

### Community 22 - "Community 22"
Cohesion: 0.33
Nodes (2): AiInsightRepository, AiInsightEntity

### Community 23 - "Community 23"
Cohesion: 0.33
Nodes (1): OpenRouterAdapterTest

### Community 24 - "Community 24"
Cohesion: 0.33
Nodes (1): RateLimiterServiceTest

### Community 25 - "Community 25"
Cohesion: 0.33
Nodes (1): ConversationStateUseCase

### Community 26 - "Community 26"
Cohesion: 0.33
Nodes (1): UserNoteUseCase

### Community 27 - "Community 27"
Cohesion: 0.33
Nodes (1): WeightHistoryUseCase

### Community 28 - "Community 28"
Cohesion: 0.33
Nodes (1): WorkoutQueryUseCase

### Community 29 - "Community 29"
Cohesion: 0.33
Nodes (1): NutritionCommandPort

### Community 30 - "Community 30"
Cohesion: 0.33
Nodes (1): UserNotePersistencePort

### Community 31 - "Community 31"
Cohesion: 0.40
Nodes (2): UserNoteJpaRepository, UserNote

### Community 32 - "Community 32"
Cohesion: 0.40
Nodes (2): WeightHistoryJpaRepository, WeightHistory

### Community 33 - "Community 33"
Cohesion: 0.40
Nodes (1): UserPort

### Community 34 - "Community 34"
Cohesion: 0.40
Nodes (1): CurrentUserApi

### Community 35 - "Community 35"
Cohesion: 0.40
Nodes (1): SecurityConfig

### Community 37 - "Community 37"
Cohesion: 0.40
Nodes (1): CommandHandler

### Community 38 - "Community 38"
Cohesion: 0.40
Nodes (1): MemoryQueryUseCase

### Community 39 - "Community 39"
Cohesion: 0.40
Nodes (1): NutritionQueryUseCase

### Community 40 - "Community 40"
Cohesion: 0.50
Nodes (3): Long, ProfileJpaRepositoryImpl, Profile

### Community 41 - "Community 41"
Cohesion: 0.50
Nodes (4): conversation_history, conversation_state, telegram_users, users

### Community 42 - "Community 42"
Cohesion: 0.40
Nodes (1): TelegramLinkCodeManager

### Community 43 - "Community 43"
Cohesion: 0.40
Nodes (1): TelegramLinkService

### Community 44 - "Community 44"
Cohesion: 0.50
Nodes (1): AiClientConfig

### Community 46 - "Community 46"
Cohesion: 0.50
Nodes (1): GeminiAdapterTest

### Community 47 - "Community 47"
Cohesion: 0.50
Nodes (1): RateLimiterService

### Community 48 - "Community 48"
Cohesion: 0.67
Nodes (2): WebMvcConfig, WebMvcConfigurer

### Community 49 - "Community 49"
Cohesion: 0.50
Nodes (1): ProfileUseCase

### Community 50 - "Community 50"
Cohesion: 0.50
Nodes (1): SyncNutritionUseCase

### Community 51 - "Community 51"
Cohesion: 0.50
Nodes (1): ModuleTest

### Community 52 - "Community 52"
Cohesion: 0.50
Nodes (1): WorkoutParserPort

### Community 53 - "Community 53"
Cohesion: 0.67
Nodes (2): WorkoutExerciseJpaRepository, WorkoutExerciseJpaEntity

### Community 54 - "Community 54"
Cohesion: 0.67
Nodes (2): WorkoutJpaRepository, WorkoutJpaEntity

### Community 55 - "Community 55"
Cohesion: 0.50
Nodes (1): MemoryEventListener

### Community 56 - "Community 56"
Cohesion: 0.50
Nodes (1): TelegramBotService

### Community 57 - "Community 57"
Cohesion: 0.67
Nodes (1): FitnessAiTools

### Community 58 - "Community 58"
Cohesion: 0.67
Nodes (1): UserApi

### Community 59 - "Community 59"
Cohesion: 0.67
Nodes (1): MemoryConfig

### Community 60 - "Community 60"
Cohesion: 0.67
Nodes (1): TelegramBotConfig

### Community 61 - "Community 61"
Cohesion: 0.67
Nodes (1): WebConfig

### Community 62 - "Community 62"
Cohesion: 0.67
Nodes (1): ConversationStateEntity

### Community 63 - "Community 63"
Cohesion: 0.67
Nodes (1): FitnessAppApplication

### Community 64 - "Community 64"
Cohesion: 0.67
Nodes (1): FitnessAppApplicationTests

### Community 65 - "Community 65"
Cohesion: 0.67
Nodes (1): ImportWorkoutUseCase

### Community 66 - "Community 66"
Cohesion: 0.67
Nodes (1): LoginUseCase

### Community 67 - "Community 67"
Cohesion: 0.67
Nodes (1): MonthlyReportController

### Community 68 - "Community 68"
Cohesion: 0.67
Nodes (1): RegisterUserPort

### Community 69 - "Community 69"
Cohesion: 0.67
Nodes (1): TelegramAiResponseListener

### Community 70 - "Community 70"
Cohesion: 0.67
Nodes (1): WeeklyReportController

### Community 71 - "Community 71"
Cohesion: 0.67
Nodes (1): CaffeineCacheConfig

### Community 72 - "Community 72"
Cohesion: 1.00
Nodes (2): ai_insights, users

### Community 73 - "Community 73"
Cohesion: 1.00
Nodes (2): users, weight_history

### Community 74 - "Community 74"
Cohesion: 0.67
Nodes (1): NutritionMonthlyApi

### Community 75 - "Community 75"
Cohesion: 0.67
Nodes (2): DailyMacrosDto, NutritionMonthlyStatsDto

### Community 76 - "Community 76"
Cohesion: 0.67
Nodes (1): NutritionWeeklyApi

### Community 77 - "Community 77"
Cohesion: 0.67
Nodes (2): DailyMacrosDto, NutritionWeeklyStatsDto

### Community 78 - "Community 78"
Cohesion: 0.67
Nodes (1): AiModelPort

### Community 79 - "Community 79"
Cohesion: 0.67
Nodes (1): UserPersistencePort

### Community 80 - "Community 80"
Cohesion: 0.67
Nodes (1): WorkoutPersistencePort

### Community 81 - "Community 81"
Cohesion: 0.67
Nodes (1): TimeEntryUtil

### Community 82 - "Community 82"
Cohesion: 0.67
Nodes (1): WorkoutJdbcQueryAdapterSqlTest

### Community 83 - "Community 83"
Cohesion: 0.67
Nodes (1): WorkoutQueryUseCaseTest

### Community 84 - "Community 84"
Cohesion: 0.67
Nodes (1): WorkoutMonthlyApi

### Community 85 - "Community 85"
Cohesion: 0.67
Nodes (1): WorkoutWeeklyApi

### Community 86 - "Community 86"
Cohesion: 1.00
Nodes (1): AiInsightEntity

### Community 87 - "Community 87"
Cohesion: 1.00
Nodes (1): TelegramProperties

### Community 88 - "Community 88"
Cohesion: 1.00
Nodes (1): Profile

### Community 89 - "Community 89"
Cohesion: 1.00
Nodes (1): TelegramUserEntity

### Community 90 - "Community 90"
Cohesion: 1.00
Nodes (1): UserNote

### Community 91 - "Community 91"
Cohesion: 1.00
Nodes (1): WeightHistory

### Community 92 - "Community 92"
Cohesion: 1.00
Nodes (1): WorkoutExerciseJpaEntity

### Community 93 - "Community 93"
Cohesion: 1.00
Nodes (1): WorkoutJpaEntity

### Community 94 - "Community 94"
Cohesion: 1.00
Nodes (1): WorkoutSetJpaEntity

### Community 95 - "Community 95"
Cohesion: 1.00
Nodes (1): WorkoutSummaryDto

### Community 96 - "Community 96"
Cohesion: 1.00
Nodes (1): WorkoutSummaryWeeklyDto

### Community 97 - "Community 97"
Cohesion: 1.00
Nodes (1): user_notes

### Community 98 - "Community 98"
Cohesion: 1.00
Nodes (1): user_memory

### Community 99 - "Community 99"
Cohesion: 1.00
Nodes (1): NutritionSyncScheduler

### Community 100 - "Community 100"
Cohesion: 1.00
Nodes (1): WorkoutMonthlyStatsDto

### Community 101 - "Community 101"
Cohesion: 1.00
Nodes (1): WorkoutWeeklyStatsDto

## Knowledge Gaps
- **23 isolated node(s):** `AiInsightEntity`, `UserNote`, `NutritionMonthlyStatsDto`, `DailyMacrosDto`, `NutritionWeeklyStatsDto` (+18 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 17`** (1 nodes): `NutritionMonthlyApiTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 18`** (1 nodes): `CodeHygieneTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 21`** (2 nodes): `MemoryQueryUseCase`, `MemoryService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 22`** (2 nodes): `AiInsightRepository`, `AiInsightEntity`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 23`** (1 nodes): `OpenRouterAdapterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 24`** (1 nodes): `RateLimiterServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 25`** (1 nodes): `ConversationStateUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 26`** (1 nodes): `UserNoteUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 27`** (1 nodes): `WeightHistoryUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 28`** (1 nodes): `WorkoutQueryUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 29`** (1 nodes): `NutritionCommandPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 30`** (1 nodes): `UserNotePersistencePort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 31`** (2 nodes): `UserNoteJpaRepository`, `UserNote`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 32`** (2 nodes): `WeightHistoryJpaRepository`, `WeightHistory`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 33`** (1 nodes): `UserPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 34`** (1 nodes): `CurrentUserApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 35`** (1 nodes): `SecurityConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 37`** (1 nodes): `CommandHandler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 38`** (1 nodes): `MemoryQueryUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 39`** (1 nodes): `NutritionQueryUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 42`** (1 nodes): `TelegramLinkCodeManager`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 43`** (1 nodes): `TelegramLinkService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 44`** (1 nodes): `AiClientConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 46`** (1 nodes): `GeminiAdapterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 47`** (1 nodes): `RateLimiterService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 48`** (2 nodes): `WebMvcConfig`, `WebMvcConfigurer`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 49`** (1 nodes): `ProfileUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 50`** (1 nodes): `SyncNutritionUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 51`** (1 nodes): `ModuleTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 52`** (1 nodes): `WorkoutParserPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 53`** (2 nodes): `WorkoutExerciseJpaRepository`, `WorkoutExerciseJpaEntity`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 54`** (2 nodes): `WorkoutJpaRepository`, `WorkoutJpaEntity`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 55`** (1 nodes): `MemoryEventListener`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 56`** (1 nodes): `TelegramBotService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 57`** (1 nodes): `FitnessAiTools`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 58`** (1 nodes): `UserApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 59`** (1 nodes): `MemoryConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 60`** (1 nodes): `TelegramBotConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 61`** (1 nodes): `WebConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 62`** (1 nodes): `ConversationStateEntity`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 63`** (1 nodes): `FitnessAppApplication`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 64`** (1 nodes): `FitnessAppApplicationTests`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 65`** (1 nodes): `ImportWorkoutUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 66`** (1 nodes): `LoginUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 67`** (1 nodes): `MonthlyReportController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 68`** (1 nodes): `RegisterUserPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 69`** (1 nodes): `TelegramAiResponseListener`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 70`** (1 nodes): `WeeklyReportController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 71`** (1 nodes): `CaffeineCacheConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 72`** (2 nodes): `ai_insights`, `users`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 73`** (2 nodes): `users`, `weight_history`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 74`** (1 nodes): `NutritionMonthlyApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 75`** (2 nodes): `DailyMacrosDto`, `NutritionMonthlyStatsDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 76`** (1 nodes): `NutritionWeeklyApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 77`** (2 nodes): `DailyMacrosDto`, `NutritionWeeklyStatsDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 78`** (1 nodes): `AiModelPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 79`** (1 nodes): `UserPersistencePort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 80`** (1 nodes): `WorkoutPersistencePort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 81`** (1 nodes): `TimeEntryUtil`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 82`** (1 nodes): `WorkoutJdbcQueryAdapterSqlTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 83`** (1 nodes): `WorkoutQueryUseCaseTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 84`** (1 nodes): `WorkoutMonthlyApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 85`** (1 nodes): `WorkoutWeeklyApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 86`** (1 nodes): `AiInsightEntity`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 87`** (1 nodes): `TelegramProperties`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 88`** (1 nodes): `Profile`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 89`** (1 nodes): `TelegramUserEntity`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 90`** (1 nodes): `UserNote`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 91`** (1 nodes): `WeightHistory`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 92`** (1 nodes): `WorkoutExerciseJpaEntity`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 93`** (1 nodes): `WorkoutJpaEntity`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 94`** (1 nodes): `WorkoutSetJpaEntity`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 95`** (1 nodes): `WorkoutSummaryDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 96`** (1 nodes): `WorkoutSummaryWeeklyDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 97`** (1 nodes): `user_notes`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 98`** (1 nodes): `user_memory`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 99`** (1 nodes): `NutritionSyncScheduler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 100`** (1 nodes): `WorkoutMonthlyStatsDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 101`** (1 nodes): `WorkoutWeeklyStatsDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TelegramUserRepository` connect `Community 1` to `Community 20`, `Community 40`?**
  _High betweenness centrality (0.015) - this node is a cross-community bridge._
- **What connects `AiInsightEntity`, `UserNote`, `NutritionMonthlyStatsDto` to the rest of the system?**
  _23 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.05673758865248227 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.050241545893719805 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.06756756756756757 - nodes in this community are weakly interconnected._
- **Should `Community 3` be split into smaller, more focused modules?**
  _Cohesion score 0.07096774193548387 - nodes in this community are weakly interconnected._
- **Should `Community 4` be split into smaller, more focused modules?**
  _Cohesion score 0.09195402298850575 - nodes in this community are weakly interconnected._