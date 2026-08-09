# Graph Report - .  (2026-08-09)

## Corpus Check
- Large corpus: 530 files · ~294 495 words. Semantic extraction will be expensive (many Claude tokens). Consider running on a subfolder, or use --no-semantic to run AST-only.

## Summary
- 1905 nodes · 3431 edges · 190 communities detected
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 69 edges (avg confidence: 0.5)
- Token cost: 0 input · 0 output
- Edge kinds: method: 872 · MODIFIES: 846 · calls: 481 · contains: 454 · imports: 220 · PARENT_OF: 136 · ON_BRANCH: 129 · rationale_for: 97 · uses: 69 · inherits: 51 · implements: 47 · imports_from: 19 · references: 10


## Input Scope
- Requested: auto
- Resolved: committed (source: default-auto)
- Included files: 530 · Candidates: 810
- Excluded: 33 untracked · 1139 ignored · 29 sensitive · 4 missing committed
- Recommendation: Use --scope all or graphify.yaml inputs.corpus for a knowledge-base folder.

## Graph Freshness
- Built from Git commit: `6c87a7a`
- Compare this hash to `git rev-parse HEAD` before trusting freshness-sensitive graph output.
## God Nodes (most connected - your core abstractions)
1. `CodeHygieneTest` - 43 edges
2. `FitnessAiService` - 30 edges
3. `Profile` - 28 edges
4. `ObservationEvent` - 21 edges
5. `_make_project()` - 20 edges
6. `User` - 20 edges
7. `GlobalExceptionHandler` - 19 edges
8. `JefitCsvParserAdapter` - 19 edges
9. `WorkoutPersistenceAdapterPostgresIntegrationTest` - 18 edges
10. `ComplianceSpec` - 17 edges

## Surprising Connections (you probably didn't know these)
- `Generate compliance specs from skill files using LLM.` --uses--> `ComplianceSpec`  [INFERRED]
  .gemini/skills/skill-comply/scripts/spec_generator.py → .gemini/skills/skill-comply/scripts/parser.py
- `Generate a compliance spec from a skill/rule file.      Calls claude -p with t` --uses--> `ComplianceSpec`  [INFERRED]
  .gemini/skills/skill-comply/scripts/spec_generator.py → .gemini/skills/skill-comply/scripts/parser.py
- `Generate Markdown compliance reports.` --uses--> `Scenario`  [INFERRED]
  .gemini/skills/skill-comply/scripts/report.py → .gemini/skills/skill-comply/scripts/scenario_generator.py
- `Generate a Markdown compliance report.      Args:         skill_path: Path to` --uses--> `Scenario`  [INFERRED]
  .gemini/skills/skill-comply/scripts/report.py → .gemini/skills/skill-comply/scripts/scenario_generator.py
- `Run scenarios via claude -p and parse tool calls from stream-json output.` --uses--> `ObservationEvent`  [INFERRED]
  .gemini/skills/skill-comply/scripts/runner.py → .gemini/skills/skill-comply/scripts/parser.py

## Communities

### Community 0 - "Community 0"
Cohesion: 0.07
Nodes (54): cmd_evolve(), cmd_export(), cmd_import(), cmd_projects(), cmd_promote(), cmd_prune(), cmd_status(), _collect_pending_dirs() (+46 more)

### Community 1 - "Community 1"
Cohesion: 0.07
Nodes (14): MonthlyReportOrchestrator, 20ff72a Merge pull request #22 from DmitriyGrachev/PROD_new_feat_month, 224396b HUGE REFACTORING OF AI MODULE + adapters, 3815095 Merge remote-tracking branch 'origin/PROD' into PROD, 7d205c8 feat : monthly insights done, 8971079 feat : add month nutrition api, need aditional tests and workout, MonthlyReportController, NutritionMonthlyApi (+6 more)

### Community 2 - "Community 2"
Cohesion: 0.07
Nodes (1): CodeHygieneTest

### Community 3 - "Community 3"
Cohesion: 0.09
Nodes (35): stabilization/test-security-ci, 00af208 fix: limit telegram link attempts, 08c2c56 fix: expose secured actuator endpoints, 0d11de1 docs: design P0 data foundation phase, 12f46f9 conf : gitignore file, 1401f78 chore: add Codex project guidance, 14d36eb fix: harden security backlog items, 152966d fix: remove sensitive ai and telegram logging (+27 more)

### Community 4 - "Community 4"
Cohesion: 0.08
Nodes (20): WeeklyReportOrchestrator, OpenApiEndpoint, 12e4208 fix: update spring boot tomcat line, 19c38a8 fix: encrypt fatsecret tokens, 312bbea fix: update spring ai cve line, 33ecbc9 fix: restore user memory hnsw index, 354b44f fix: externalize ai prompt templates, 568e12e fix: update jjwt line (+12 more)

### Community 5 - "Community 5"
Cohesion: 0.06
Nodes (11): 12c65d2 made indopot, 17e3f61 fix: remove stale telegram link service, 8d77d5e feat: complete migration to the new architecture, d8898bd fix: expose workout import warnings, ImportWorkoutUseCase, CaffeineCacheConfig, WorkoutParserPort, WorkoutPersistencePort (+3 more)

### Community 6 - "Community 6"
Cohesion: 0.11
Nodes (15): AiInsightEntity, 1c62725 fix: scope jefit exercise lookup by workout, 20eb08b feat: include workout context in daily insights, 2d879ec refactor: split ai daily and telegram workflows, 3b61974 Stabilize nutrition and workout AI workflows, 418dbcd Stabilize AI workout insight workflow, 426f943 Fix nutrition workout AI insight workflows, 78ce737 refactor: expose nutrition sync event contract (+7 more)

### Community 7 - "Community 7"
Cohesion: 0.09
Nodes (15): AiInsightRepository, ConversationStateEntity, JpaRepository, Long, ProfileJpaRepositoryImpl, UserNoteJpaRepository, WeightHistoryJpaRepository, ConversationStateRepository (+7 more)

### Community 8 - "Community 8"
Cohesion: 0.14
Nodes (11): AiModelPort, 03e0965 cleaned up, 2ffe3ae chore: normalize code comments, 31ee683 temp clean, 5896320 feat : added detailed recent insights, 7a1c705 Merge pull request #25 from DmitriyGrachev/feature/analysis-and-fixes, b47a94f feat : shart/long time memory impl, f93315c feat : added vector user_memory (+3 more)

### Community 9 - "Community 9"
Cohesion: 0.17
Nodes (1): FitnessAiService

### Community 10 - "Community 10"
Cohesion: 0.13
Nodes (11): 3b09783 rafactoring: Created seperate auth modul for Security and User, 42a3900 Merge pull request #17 from DmitriyGrachev/PROD_refactor-auth-module, 62d0203 rafactoring: Added SPRING MODULITH, made initial changes for architecture, c4a2547 feat: Add flyway, Initial before tests, e9c30d5 Validate auth request payloads, ed14a96 Merge pull request #20 from DmitriyGrachev/PROD_Flyway_Migration, WebConfig, WorkoutJpaEntity (+3 more)

### Community 11 - "Community 11"
Cohesion: 0.07
Nodes (1): Profile

### Community 12 - "Community 12"
Cohesion: 0.14
Nodes (11): WebMvcConfig, 02bccc7 fix: add ai router cooldown, 3d3abaa renamed, 99ea972 feat: fallback for ai models, dbd1c70 HUGE REFACTORING OF AI MODULE, dd1bf8e Merge pull request #21 from DmitriyGrachev/PROD_analytics_module, e469d36 Initial commit, f1d2018 AI start (+3 more)

### Community 13 - "Community 13"
Cohesion: 0.07
Nodes (1): Tests for continuous-learning-v2 instinct-cli.py  Covers:   - parse_instinct_

### Community 14 - "Community 14"
Cohesion: 0.09
Nodes (7): GeminiAdapterTest, UserPort, 25c83e7 fix : profile for test, 8112188 fix : utf, and added missing files, 964539f Refactor production tests and module boundaries, ab7b2cc fix : ai props, TelegramNoteListener

### Community 15 - "Community 15"
Cohesion: 0.08
Nodes (8): 91c3658 feat : telegram init, TelegramBotConfig, TelegramProperties, ConversationStateEntity, TelegramUserEntity, CommandHandler, TelegramAiResponseListener, TelegramNotificationListener

### Community 16 - "Community 16"
Cohesion: 0.10
Nodes (10): AiAuthException, AiInvalidRequestException, AiUnavailableException, ExternalApiException, ExternalServiceUnavailableException, RequiredExternalConnectionMissingException, UserAlreadyExistsException, ExternalApiException (+2 more)

### Community 17 - "Community 17"
Cohesion: 0.10
Nodes (6): CommandHandler, 6c87a7a fix: restrict Telegram linking to private chats, AskCommandHandler, LinkCommandHandler, StartCommandHandler, TodayCommandHandler

### Community 18 - "Community 18"
Cohesion: 0.23
Nodes (19): classify_events(), _parse_classification(), Classify tool calls against compliance steps using LLM., Classify which tool calls match which compliance steps.      Returns {step_id:, Parse LLM classification output into {step_id: [event_indices]}., _check_temporal_order(), ComplianceResult, grade() (+11 more)

### Community 19 - "Community 19"
Cohesion: 0.10
Nodes (21): _make_project(), Create project directory structure and return a project dict., Should load from both project and global directories., When project and global have same ID, project wins., load_project_only_instincts should NOT include global instincts., No instincts at all should return empty list., Status with no instincts should print fallback message., Status should show project and global instinct counts. (+13 more)

### Community 20 - "Community 20"
Cohesion: 0.16
Nodes (8): 39b4c95 Stabilize weight command tests, 4022879 Split Maven test gates, 743db4e fix: restrict Telegram commands to linked private chat, b0f381b test: cover linked Telegram today command, ConversationStateUseCase, ModuleArchitectureTest, TelegramUserRepository, TelegramUserEntity

### Community 21 - "Community 21"
Cohesion: 0.19
Nodes (6): 5e01166 Merge pull request #24 from DmitriyGrachev/feature/user-context-enhancement, fa3fa65 feat : added user_note, weight, profile(empty), users, weight_history, Profile, WeightHistory

### Community 22 - "Community 22"
Cohesion: 0.15
Nodes (19): append_event(), cleanup_pid(), default_output_dir(), ensure_private_dir(), is_fatal_error(), listen_with_retry(), log(), main() (+11 more)

### Community 23 - "Community 23"
Cohesion: 0.17
Nodes (11): AbstractPostgresIntegrationTest, datesBetween(), WorkoutImportedEvent(), 4d30761 test: keep postgres fixtures within user column limits, 9d2ce50 fix: stabilize ingestion-driven daily insights, dcfb3b3 feat: publish workout import events, e0ccccb fix: mark nutrition scheduler constructor for injection, ImportWorkoutUseCase (+3 more)

### Community 24 - "Community 24"
Cohesion: 0.16
Nodes (7): 0219941 Scope user notes to authenticated users, 7deb6c9 Fix auth boundaries and monthly report period, 89603cd feat : add user notes, user_notes, UserNoteService, UserNotePersistencePort, UserNoteUseCase

### Community 25 - "Community 25"
Cohesion: 0.13
Nodes (10): core, LoginUseCase, org, UserAuthenticationAdapter, security, LoginService, UserDetailsService, springframework (+2 more)

### Community 26 - "Community 26"
Cohesion: 0.20
Nodes (1): GlobalExceptionHandler

### Community 27 - "Community 27"
Cohesion: 0.11
Nodes (1): User

### Community 28 - "Community 28"
Cohesion: 0.13
Nodes (6): AuthEndpoints, AuthRateLimitWebTest, 2e37542 chore: baseline before error contract, 788af50 feat: harden security configuration, ad509a8 feat: add unified api error contract, ErrorCode

### Community 29 - "Community 29"
Cohesion: 0.12
Nodes (4): UserAuthorityTest, UserPersistenceAdapterTest, UserRepository, User

### Community 30 - "Community 30"
Cohesion: 0.35
Nodes (1): JefitCsvParserAdapter

### Community 31 - "Community 31"
Cohesion: 0.16
Nodes (11): 3bdb197 docs: plan ingestion idempotency work, 4236495 bug: Fixed date in workouts, 50d2833 fix: make workout import idempotent, 625a7ac featur: add workout weekly stat, 688f01d feat: base for analytics module, 69f0ed7 feat: Added mini CQRS bug: Fixed timestamp and localdatetime in parsing issue, 8b45fec fix: make workout persistence save transactional, 96d9801 Base read query (+3 more)

### Community 32 - "Community 32"
Cohesion: 0.21
Nodes (1): WorkoutPersistenceAdapterPostgresIntegrationTest

### Community 33 - "Community 33"
Cohesion: 0.17
Nodes (2): MutableClock, SmartAiRouterTest

### Community 34 - "Community 34"
Cohesion: 0.15
Nodes (7): MonthlyReportOrchestratorTest, UserPersistenceAdapter, RegisterUserPort, RegisterService, RegisterServiceTest, UserApi, UserPersistencePort

### Community 35 - "Community 35"
Cohesion: 0.17
Nodes (6): Clock, 64dd819 fix: reduce nutrition sync churn, NutritionServicePrivacyLoggingTest, NutritionCommandPort, MemoryCleanupService, SyncNutritionUseCase

### Community 36 - "Community 36"
Cohesion: 0.13
Nodes (1): UserNote

### Community 37 - "Community 37"
Cohesion: 0.22
Nodes (14): _parse_stream_json(), Run scenarios via claude -p and parse tool calls from stream-json output., Execute a scenario and extract tool calls from stream-json output., Sanitize scenario ID and ensure path stays within sandbox base., Create sandbox directory and run setup commands., Parse claude -p stream-json output into ObservationEvents.      Stream-json fo, run_scenario(), _safe_sandbox_dir() (+6 more)

### Community 38 - "Community 38"
Cohesion: 0.18
Nodes (3): AiControllerTest, AiTodayInsightResponse, badd79d fix: type controller responses

### Community 39 - "Community 39"
Cohesion: 0.23
Nodes (1): NutritionPersistenceAdapter

### Community 40 - "Community 40"
Cohesion: 0.23
Nodes (1): DailyInsightServiceTest

### Community 41 - "Community 41"
Cohesion: 0.16
Nodes (8): 2b1a12a chore: baseline before security hardening, CLI entry point for skill-comply., generate_spec(), Generate compliance specs from skill files using LLM., Generate a compliance spec from a skill/rule file.      Calls claude -p with t, extract_yaml(), Shared utilities for skill-comply scripts., Extract YAML from LLM output, stripping markdown fences if present.

### Community 42 - "Community 42"
Cohesion: 0.15
Nodes (3): eab64da fix: track nutrition sync freshness hashes, fa52037 fix: restore daily nutrition sync scheduler, NutritionCommandPort

### Community 43 - "Community 43"
Cohesion: 0.14
Nodes (1): WeightHistory

### Community 44 - "Community 44"
Cohesion: 0.21
Nodes (5): DailyInsight, MoeOrchestratorTest, MonthlyReport, QuickAnalysis, WeeklyReport

### Community 45 - "Community 45"
Cohesion: 0.19
Nodes (2): SecurityConfigWebTest, TestEndpoints

### Community 46 - "Community 46"
Cohesion: 0.19
Nodes (5): 0543b9f fix: return sync completion status, 1f9132d fix: validate nutrition date range, ConnectFatSecretUseCase, NutritionControllerSyncTest, NutritionControllerValidationTest

### Community 47 - "Community 47"
Cohesion: 0.15
Nodes (2): ApiErrorHandlerWebTest, ErrorEndpoints

### Community 48 - "Community 48"
Cohesion: 0.15
Nodes (2): DailyMacrosDto, NutritionWeeklyStatsDto

### Community 49 - "Community 49"
Cohesion: 0.15
Nodes (5): _mock_compliant_classification(), _mock_noncompliant_classification(), Simulate LLM correctly classifying a compliant trace., Simulate LLM classifying a noncompliant trace (impl before test)., TestGradeEdgeCases

### Community 50 - "Community 50"
Cohesion: 0.33
Nodes (1): WorkoutPersistenceAdapterTest

### Community 51 - "Community 51"
Cohesion: 0.17
Nodes (1): FitnessAiServiceDelegationTest

### Community 52 - "Community 52"
Cohesion: 0.23
Nodes (4): RateLimiterService, AiRateLimitApi, AiRateLimitApi, 95dd0cf refactor: decouple telegram ask rate limiting

### Community 53 - "Community 53"
Cohesion: 0.23
Nodes (5): 746f016 fix: add workout analytic path alias, CurrentUserApi, CurrentUserService, WorkoutAnalyticController, WorkoutQueryUseCase

### Community 54 - "Community 54"
Cohesion: 0.30
Nodes (1): WeightCommandHandlerTest

### Community 55 - "Community 55"
Cohesion: 0.27
Nodes (1): NutritionPersistenceAdapterPostgresIntegrationTest

### Community 56 - "Community 56"
Cohesion: 0.32
Nodes (1): DailyInsightSnapshotServiceTest

### Community 57 - "Community 57"
Cohesion: 0.18
Nodes (1): AuthControllerTest

### Community 58 - "Community 58"
Cohesion: 0.29
Nodes (10): event_publication, fatsecret_connection, fatsecret_day, fatsecret_food, profile, user_roles, users, workout (+2 more)

### Community 59 - "Community 59"
Cohesion: 0.29
Nodes (1): NutritionServiceSyncWorkflowTest

### Community 60 - "Community 60"
Cohesion: 0.47
Nodes (1): FitnessAiServiceReportFreshnessTest

### Community 61 - "Community 61"
Cohesion: 0.24
Nodes (4): FakeMemoryCleanupPort, MemoryCleanupServiceTest, MemoryCleanupPort, JdbcMemoryCleanupAdapter

### Community 62 - "Community 62"
Cohesion: 0.33
Nodes (1): WorkoutJdbcQueryAdapter

### Community 63 - "Community 63"
Cohesion: 0.27
Nodes (1): TelegramLinkCodeManager

### Community 64 - "Community 64"
Cohesion: 0.25
Nodes (5): RateLimitInterceptor, UserNoteControllerValidationTest, c4f8830 Fail closed when AI user cannot be resolved, d7f01f1 clean up, HandlerInterceptor

### Community 65 - "Community 65"
Cohesion: 0.53
Nodes (1): SecurityProtectedEndpointsWebTest

### Community 66 - "Community 66"
Cohesion: 0.25
Nodes (2): NutritionDay(), safeEntries()

### Community 67 - "Community 67"
Cohesion: 0.50
Nodes (1): MemoryServiceTest

### Community 68 - "Community 68"
Cohesion: 0.39
Nodes (1): NutritionMonthlyApiTest

### Community 69 - "Community 69"
Cohesion: 0.36
Nodes (1): NutritionPersistenceAdapterIdempotencyTest

### Community 70 - "Community 70"
Cohesion: 0.22
Nodes (1): TelegramMessages

### Community 71 - "Community 71"
Cohesion: 0.32
Nodes (5): AiInsightEntity, 618a93e feature: add transcatinal outbox, fc44122 feature: add init events, ai_insights, users

### Community 72 - "Community 72"
Cohesion: 0.25
Nodes (3): 641c78f fix: clean up expired user memory, MemoryCleanupPort, MemoryCleanupScheduler

### Community 73 - "Community 73"
Cohesion: 0.39
Nodes (2): OncePerRequestFilter, AuthRateLimitFilter

### Community 74 - "Community 74"
Cohesion: 0.50
Nodes (1): WorkoutJdbcQueryAdapterIntegrationTest

### Community 75 - "Community 75"
Cohesion: 0.50
Nodes (1): WorkoutPersistenceAdapter

### Community 76 - "Community 76"
Cohesion: 0.39
Nodes (1): TelegramAskAiServiceTest

### Community 77 - "Community 77"
Cohesion: 0.39
Nodes (1): TelegramBotService

### Community 78 - "Community 78"
Cohesion: 0.25
Nodes (1): NutritionController

### Community 79 - "Community 79"
Cohesion: 0.25
Nodes (1): WorkoutDailyStatsDto

### Community 80 - "Community 80"
Cohesion: 0.25
Nodes (1): WorkoutWeeklyStatsDto

### Community 81 - "Community 81"
Cohesion: 0.48
Nodes (1): SmartAiRouter

### Community 82 - "Community 82"
Cohesion: 0.43
Nodes (1): LinkCommandHandlerTest

### Community 83 - "Community 83"
Cohesion: 0.43
Nodes (1): NoteCommandHandler

### Community 84 - "Community 84"
Cohesion: 0.52
Nodes (1): NoteCommandHandlerTest

### Community 85 - "Community 85"
Cohesion: 0.43
Nodes (1): TelegramUpdateHandlerPrivacyLoggingTest

### Community 86 - "Community 86"
Cohesion: 0.52
Nodes (1): MemoryPgVectorIntegrationTest

### Community 87 - "Community 87"
Cohesion: 0.33
Nodes (3): WorkoutExerciseJpaRepository, WorkoutExerciseJpaEntityMappingTest, WorkoutExerciseJpaEntity

### Community 88 - "Community 88"
Cohesion: 0.33
Nodes (6): Detector, parse_spec(), parse_trace(), Parse observation traces (JSONL) and compliance specs (YAML)., Parse a JSONL observation trace file into sorted events., Parse a YAML compliance spec file.

### Community 89 - "Community 89"
Cohesion: 0.52
Nodes (1): DailyInsightService

### Community 90 - "Community 90"
Cohesion: 0.29
Nodes (2): write_test has before_step=write_impl, but test is written AFTER impl., TestGradeNoncompliant

### Community 91 - "Community 91"
Cohesion: 0.29
Nodes (1): TestGradeCompliant

### Community 92 - "Community 92"
Cohesion: 0.29
Nodes (1): TestParseTrace

### Community 93 - "Community 93"
Cohesion: 0.52
Nodes (1): WorkoutImportServiceTest

### Community 94 - "Community 94"
Cohesion: 0.33
Nodes (1): OpenRouterAdapterTest

### Community 95 - "Community 95"
Cohesion: 0.47
Nodes (1): SecurityConfig

### Community 96 - "Community 96"
Cohesion: 0.73
Nodes (1): AskCommandHandlerTest

### Community 97 - "Community 97"
Cohesion: 0.40
Nodes (1): StartCommandHandlerTest

### Community 98 - "Community 98"
Cohesion: 0.47
Nodes (1): WeightCommandHandler

### Community 99 - "Community 99"
Cohesion: 0.33
Nodes (1): ConversationStateUseCase

### Community 100 - "Community 100"
Cohesion: 0.40
Nodes (2): TelegramUpdateHandler, TelegramLongPollingBot

### Community 101 - "Community 101"
Cohesion: 0.33
Nodes (2): MemoryQueryUseCase, MemoryService

### Community 102 - "Community 102"
Cohesion: 0.60
Nodes (1): OpenRouterAdapter

### Community 103 - "Community 103"
Cohesion: 0.40
Nodes (1): NutritionJdbcQueryAdapter

### Community 104 - "Community 104"
Cohesion: 0.40
Nodes (2): UserAdapter, UserPort

### Community 105 - "Community 105"
Cohesion: 0.40
Nodes (1): UserNotePersistenceAdapter

### Community 106 - "Community 106"
Cohesion: 0.40
Nodes (1): WeightHistoryRepository

### Community 107 - "Community 107"
Cohesion: 0.40
Nodes (1): ConversationStateService

### Community 109 - "Community 109"
Cohesion: 0.47
Nodes (1): NutritionService

### Community 110 - "Community 110"
Cohesion: 0.33
Nodes (1): NutritionSyncSchedulerTest

### Community 111 - "Community 111"
Cohesion: 0.33
Nodes (1): TestParseSpec

### Community 112 - "Community 112"
Cohesion: 0.53
Nodes (1): JwtCore

### Community 113 - "Community 113"
Cohesion: 0.53
Nodes (1): JefitCsvParserAdapterTest

### Community 114 - "Community 114"
Cohesion: 0.70
Nodes (1): AiController

### Community 115 - "Community 115"
Cohesion: 0.50
Nodes (1): AiPromptRenderer

### Community 116 - "Community 116"
Cohesion: 0.40
Nodes (1): RateLimiterServiceTest

### Community 118 - "Community 118"
Cohesion: 0.50
Nodes (1): ApiErrorResponseWriter

### Community 119 - "Community 119"
Cohesion: 0.40
Nodes (1): TestGenerateHandler

### Community 120 - "Community 120"
Cohesion: 0.60
Nodes (1): TodayCommandHandlerTest

### Community 121 - "Community 121"
Cohesion: 0.40
Nodes (1): NutritionQueryUseCase

### Community 122 - "Community 122"
Cohesion: 0.40
Nodes (1): UserNoteUseCase

### Community 123 - "Community 123"
Cohesion: 0.40
Nodes (1): WeightHistoryUseCase

### Community 124 - "Community 124"
Cohesion: 0.40
Nodes (1): WorkoutQueryUseCase

### Community 125 - "Community 125"
Cohesion: 0.50
Nodes (4): conversation_history, conversation_state, telegram_users, users

### Community 126 - "Community 126"
Cohesion: 0.40
Nodes (1): UserAuthenticationPort

### Community 127 - "Community 127"
Cohesion: 0.40
Nodes (1): UserNotePersistencePort

### Community 128 - "Community 128"
Cohesion: 0.80
Nodes (4): generate_report(), _overall_compliance(), _step_compliance_rate(), _steps_to_promote()

### Community 129 - "Community 129"
Cohesion: 0.60
Nodes (1): MemoryEventListener

### Community 130 - "Community 130"
Cohesion: 0.50
Nodes (1): NutritionSyncScheduler

### Community 131 - "Community 131"
Cohesion: 0.80
Nodes (1): WorkoutAnalyticControllerTest

### Community 132 - "Community 132"
Cohesion: 0.40
Nodes (1): WorkoutJdbcQueryAdapterSqlTest

### Community 133 - "Community 133"
Cohesion: 0.50
Nodes (1): AiPromptRendererTest

### Community 134 - "Community 134"
Cohesion: 0.50
Nodes (1): RateLimitInterceptorTest

### Community 135 - "Community 135"
Cohesion: 0.67
Nodes (1): ActuatorSecurityWebTest

### Community 136 - "Community 136"
Cohesion: 0.50
Nodes (1): ActuatorEndpoints

### Community 137 - "Community 137"
Cohesion: 0.50
Nodes (1): CurrentUserApi

### Community 138 - "Community 138"
Cohesion: 0.83
Nodes (1): OpenApiSecurityWebTest

### Community 139 - "Community 139"
Cohesion: 0.50
Nodes (1): UserNoteControllerTest

### Community 140 - "Community 140"
Cohesion: 0.83
Nodes (1): UserNoteForeignKeyIntegrationTest

### Community 141 - "Community 141"
Cohesion: 0.50
Nodes (1): MemoryQueryUseCase

### Community 142 - "Community 142"
Cohesion: 0.50
Nodes (1): SyncNutritionUseCase

### Community 143 - "Community 143"
Cohesion: 0.50
Nodes (1): MemoryEventListenerTest

### Community 144 - "Community 144"
Cohesion: 0.50
Nodes (1): TelegramWeightListenerPrivacyLoggingTest

### Community 145 - "Community 145"
Cohesion: 0.67
Nodes (1): GeminiAdapter

### Community 146 - "Community 146"
Cohesion: 0.67
Nodes (1): ProfileJpaRepository

### Community 147 - "Community 147"
Cohesion: 0.83
Nodes (1): DailyInsightSnapshotService

### Community 148 - "Community 148"
Cohesion: 0.67
Nodes (1): TelegramAskAiService

### Community 149 - "Community 149"
Cohesion: 0.50
Nodes (1): UserNoteController

### Community 150 - "Community 150"
Cohesion: 0.67
Nodes (1): AiClientConfig

### Community 151 - "Community 151"
Cohesion: 0.67
Nodes (1): MoeOrchestrator

### Community 152 - "Community 152"
Cohesion: 1.00
Nodes (1): MonthlyReportTransactionService

### Community 153 - "Community 153"
Cohesion: 1.00
Nodes (1): WeeklyReportTransactionService

### Community 154 - "Community 154"
Cohesion: 0.67
Nodes (1): UserApi

### Community 155 - "Community 155"
Cohesion: 0.67
Nodes (1): UserRepositorySignatureTest

### Community 156 - "Community 156"
Cohesion: 0.67
Nodes (1): ProfileUseCase

### Community 157 - "Community 157"
Cohesion: 0.67
Nodes (1): WeeklyReportController

### Community 158 - "Community 158"
Cohesion: 1.00
Nodes (2): user_notes, users

### Community 159 - "Community 159"
Cohesion: 0.67
Nodes (1): NutritionWeeklyApi

### Community 160 - "Community 160"
Cohesion: 0.67
Nodes (1): WeightHistoryRepositoryTest

### Community 161 - "Community 161"
Cohesion: 0.67
Nodes (1): UserPersistencePort

### Community 162 - "Community 162"
Cohesion: 0.67
Nodes (1): TimeEntryUtil

### Community 163 - "Community 163"
Cohesion: 0.67
Nodes (1): AuthController

### Community 164 - "Community 164"
Cohesion: 0.67
Nodes (1): TestController

### Community 165 - "Community 165"
Cohesion: 0.67
Nodes (1): WorkoutDailyApi

### Community 166 - "Community 166"
Cohesion: 0.67
Nodes (1): WorkoutQueryUseCaseTest

### Community 167 - "Community 167"
Cohesion: 0.67
Nodes (1): WorkoutWeeklyApi

### Community 168 - "Community 168"
Cohesion: 1.00
Nodes (2): patch_globals(), Patch module-level globals to use tmp_path-based directories.

### Community 169 - "Community 169"
Cohesion: 1.00
Nodes (2): project_tree(), Create a realistic project directory tree for testing.

### Community 170 - "Community 170"
Cohesion: 1.00
Nodes (2): Instincts without an 'id' field should be silently dropped., test_parse_no_id_skipped()

### Community 171 - "Community 171"
Cohesion: 1.00
Nodes (2): Tilde expansion should work., test_validate_home_expansion()

### Community 172 - "Community 172"
Cohesion: 1.00
Nodes (2): Relative paths should be resolved., test_validate_relative_path()

### Community 173 - "Community 173"
Cohesion: 1.00
Nodes (2): When no git and no env var, should return global project., test_detect_project_global_fallback()

### Community 174 - "Community 174"
Cohesion: 1.00
Nodes (2): CLAUDE_PROJECT_DIR env var should be used as project root., test_detect_project_from_env()

### Community 175 - "Community 175"
Cohesion: 1.00
Nodes (2): Git timeout should fall through to global., test_detect_project_git_timeout()

### Community 176 - "Community 176"
Cohesion: 1.00
Nodes (2): detect_project should create the project dir structure., test_detect_project_creates_directories()

### Community 177 - "Community 177"
Cohesion: 1.00
Nodes (2): Loaded instincts should have _source_file, _source_type, _scope_label., test_load_annotates_metadata()

### Community 178 - "Community 178"
Cohesion: 1.00
Nodes (2): If an instinct has no 'scope' in frontmatter, it should default to scope_label., test_load_defaults_scope_from_label()

### Community 179 - "Community 179"
Cohesion: 1.00
Nodes (2): If frontmatter has explicit scope, it should be preserved., test_load_preserves_explicit_scope()

### Community 180 - "Community 180"
Cohesion: 1.00
Nodes (2): Corrupt YAML files should be warned about but not crash., test_load_handles_corrupt_file()

### Community 181 - "Community 181"
Cohesion: 1.00
Nodes (2): Global project should only load global instincts., test_load_all_global_only()

### Community 182 - "Community 182"
Cohesion: 1.00
Nodes (2): Global fallback should return global instincts for project-only queries., test_load_project_only_global_fallback_loads_global()

### Community 183 - "Community 183"
Cohesion: 1.00
Nodes (2): No projects should print helpful message., test_cmd_projects_empty_registry()

### Community 184 - "Community 184"
Cohesion: 1.00
Nodes (2): Promoting nonexistent instinct should fail., test_promote_specific_not_found()

### Community 185 - "Community 185"
Cohesion: 1.00
Nodes (2): Path-like instinct IDs should be rejected before file writes., test_promote_specific_rejects_invalid_id()

### Community 186 - "Community 186"
Cohesion: 1.00
Nodes (2): Promoting an instinct that already exists globally should fail., test_promote_specific_already_global()

### Community 187 - "Community 187"
Cohesion: 1.00
Nodes (2): Promote a project instinct to global with --force., test_promote_specific_success()

### Community 188 - "Community 188"
Cohesion: 1.00
Nodes (2): Auto-promote with no cross-project instincts should say so., test_promote_auto_no_candidates()

### Community 189 - "Community 189"
Cohesion: 1.00
Nodes (2): Dry run should list candidates but not write files., test_promote_auto_dry_run()

### Community 190 - "Community 190"
Cohesion: 1.00
Nodes (2): Auto-promote with force should write global instinct file., test_promote_auto_writes_file()

### Community 191 - "Community 191"
Cohesion: 1.00
Nodes (2): Single project should return nothing (need 2+)., test_find_cross_project_single_project()

## Knowledge Gaps
- **100 isolated node(s):** `Validate and resolve a file path, guarding against path traversal.      Raises`, `Validate instinct IDs before using them in filenames.`, `Quote a string for safe YAML frontmatter serialization.      Uses double quote`, `Detect current project context. Returns dict with id, name, root, project_dir.`, `Update the projects.json registry.      Uses file locking (where available) to` (+95 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 2`** (1 nodes): `CodeHygieneTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 9`** (1 nodes): `FitnessAiService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 11`** (1 nodes): `Profile`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 13`** (1 nodes): `Tests for continuous-learning-v2 instinct-cli.py  Covers:   - parse_instinct_`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 26`** (1 nodes): `GlobalExceptionHandler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 27`** (1 nodes): `User`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 30`** (1 nodes): `JefitCsvParserAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 32`** (1 nodes): `WorkoutPersistenceAdapterPostgresIntegrationTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 33`** (2 nodes): `MutableClock`, `SmartAiRouterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 36`** (1 nodes): `UserNote`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 39`** (1 nodes): `NutritionPersistenceAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 40`** (1 nodes): `DailyInsightServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 43`** (1 nodes): `WeightHistory`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 45`** (2 nodes): `SecurityConfigWebTest`, `TestEndpoints`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 47`** (2 nodes): `ApiErrorHandlerWebTest`, `ErrorEndpoints`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 48`** (2 nodes): `DailyMacrosDto`, `NutritionWeeklyStatsDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 50`** (1 nodes): `WorkoutPersistenceAdapterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 51`** (1 nodes): `FitnessAiServiceDelegationTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 54`** (1 nodes): `WeightCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 55`** (1 nodes): `NutritionPersistenceAdapterPostgresIntegrationTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 56`** (1 nodes): `DailyInsightSnapshotServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 57`** (1 nodes): `AuthControllerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 59`** (1 nodes): `NutritionServiceSyncWorkflowTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 60`** (1 nodes): `FitnessAiServiceReportFreshnessTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 62`** (1 nodes): `WorkoutJdbcQueryAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 63`** (1 nodes): `TelegramLinkCodeManager`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 65`** (1 nodes): `SecurityProtectedEndpointsWebTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 66`** (2 nodes): `NutritionDay()`, `safeEntries()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 67`** (1 nodes): `MemoryServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 68`** (1 nodes): `NutritionMonthlyApiTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 69`** (1 nodes): `NutritionPersistenceAdapterIdempotencyTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 70`** (1 nodes): `TelegramMessages`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 73`** (2 nodes): `OncePerRequestFilter`, `AuthRateLimitFilter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 74`** (1 nodes): `WorkoutJdbcQueryAdapterIntegrationTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 75`** (1 nodes): `WorkoutPersistenceAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 76`** (1 nodes): `TelegramAskAiServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 77`** (1 nodes): `TelegramBotService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 78`** (1 nodes): `NutritionController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 79`** (1 nodes): `WorkoutDailyStatsDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 80`** (1 nodes): `WorkoutWeeklyStatsDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 81`** (1 nodes): `SmartAiRouter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 82`** (1 nodes): `LinkCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 83`** (1 nodes): `NoteCommandHandler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 84`** (1 nodes): `NoteCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 85`** (1 nodes): `TelegramUpdateHandlerPrivacyLoggingTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 86`** (1 nodes): `MemoryPgVectorIntegrationTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 89`** (1 nodes): `DailyInsightService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 90`** (2 nodes): `write_test has before_step=write_impl, but test is written AFTER impl.`, `TestGradeNoncompliant`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 91`** (1 nodes): `TestGradeCompliant`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 92`** (1 nodes): `TestParseTrace`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 93`** (1 nodes): `WorkoutImportServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 94`** (1 nodes): `OpenRouterAdapterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 95`** (1 nodes): `SecurityConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 96`** (1 nodes): `AskCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 97`** (1 nodes): `StartCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 98`** (1 nodes): `WeightCommandHandler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 99`** (1 nodes): `ConversationStateUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 100`** (2 nodes): `TelegramUpdateHandler`, `TelegramLongPollingBot`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 101`** (2 nodes): `MemoryQueryUseCase`, `MemoryService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 102`** (1 nodes): `OpenRouterAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 103`** (1 nodes): `NutritionJdbcQueryAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 104`** (2 nodes): `UserAdapter`, `UserPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 105`** (1 nodes): `UserNotePersistenceAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 106`** (1 nodes): `WeightHistoryRepository`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 107`** (1 nodes): `ConversationStateService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 109`** (1 nodes): `NutritionService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 110`** (1 nodes): `NutritionSyncSchedulerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 111`** (1 nodes): `TestParseSpec`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 112`** (1 nodes): `JwtCore`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 113`** (1 nodes): `JefitCsvParserAdapterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 114`** (1 nodes): `AiController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 115`** (1 nodes): `AiPromptRenderer`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 116`** (1 nodes): `RateLimiterServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 118`** (1 nodes): `ApiErrorResponseWriter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 119`** (1 nodes): `TestGenerateHandler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 120`** (1 nodes): `TodayCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 121`** (1 nodes): `NutritionQueryUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 122`** (1 nodes): `UserNoteUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 123`** (1 nodes): `WeightHistoryUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 124`** (1 nodes): `WorkoutQueryUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 126`** (1 nodes): `UserAuthenticationPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 127`** (1 nodes): `UserNotePersistencePort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 129`** (1 nodes): `MemoryEventListener`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 130`** (1 nodes): `NutritionSyncScheduler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 131`** (1 nodes): `WorkoutAnalyticControllerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 132`** (1 nodes): `WorkoutJdbcQueryAdapterSqlTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 133`** (1 nodes): `AiPromptRendererTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 134`** (1 nodes): `RateLimitInterceptorTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 135`** (1 nodes): `ActuatorSecurityWebTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 136`** (1 nodes): `ActuatorEndpoints`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 137`** (1 nodes): `CurrentUserApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 138`** (1 nodes): `OpenApiSecurityWebTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 139`** (1 nodes): `UserNoteControllerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 140`** (1 nodes): `UserNoteForeignKeyIntegrationTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 141`** (1 nodes): `MemoryQueryUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 142`** (1 nodes): `SyncNutritionUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 143`** (1 nodes): `MemoryEventListenerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 144`** (1 nodes): `TelegramWeightListenerPrivacyLoggingTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 145`** (1 nodes): `GeminiAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 146`** (1 nodes): `ProfileJpaRepository`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 147`** (1 nodes): `DailyInsightSnapshotService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 148`** (1 nodes): `TelegramAskAiService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 149`** (1 nodes): `UserNoteController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 150`** (1 nodes): `AiClientConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 151`** (1 nodes): `MoeOrchestrator`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 152`** (1 nodes): `MonthlyReportTransactionService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 153`** (1 nodes): `WeeklyReportTransactionService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 154`** (1 nodes): `UserApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 155`** (1 nodes): `UserRepositorySignatureTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 156`** (1 nodes): `ProfileUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 157`** (1 nodes): `WeeklyReportController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 158`** (2 nodes): `user_notes`, `users`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 159`** (1 nodes): `NutritionWeeklyApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 160`** (1 nodes): `WeightHistoryRepositoryTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 161`** (1 nodes): `UserPersistencePort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 162`** (1 nodes): `TimeEntryUtil`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 163`** (1 nodes): `AuthController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 164`** (1 nodes): `TestController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 165`** (1 nodes): `WorkoutDailyApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 166`** (1 nodes): `WorkoutQueryUseCaseTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 167`** (1 nodes): `WorkoutWeeklyApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 168`** (2 nodes): `patch_globals()`, `Patch module-level globals to use tmp_path-based directories.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 169`** (2 nodes): `project_tree()`, `Create a realistic project directory tree for testing.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 170`** (2 nodes): `Instincts without an 'id' field should be silently dropped.`, `test_parse_no_id_skipped()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 171`** (2 nodes): `Tilde expansion should work.`, `test_validate_home_expansion()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 172`** (2 nodes): `Relative paths should be resolved.`, `test_validate_relative_path()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 173`** (2 nodes): `When no git and no env var, should return global project.`, `test_detect_project_global_fallback()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 174`** (2 nodes): `CLAUDE_PROJECT_DIR env var should be used as project root.`, `test_detect_project_from_env()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 175`** (2 nodes): `Git timeout should fall through to global.`, `test_detect_project_git_timeout()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 176`** (2 nodes): `detect_project should create the project dir structure.`, `test_detect_project_creates_directories()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 177`** (2 nodes): `Loaded instincts should have _source_file, _source_type, _scope_label.`, `test_load_annotates_metadata()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 178`** (2 nodes): `If an instinct has no 'scope' in frontmatter, it should default to scope_label.`, `test_load_defaults_scope_from_label()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 179`** (2 nodes): `If frontmatter has explicit scope, it should be preserved.`, `test_load_preserves_explicit_scope()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 180`** (2 nodes): `Corrupt YAML files should be warned about but not crash.`, `test_load_handles_corrupt_file()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 181`** (2 nodes): `Global project should only load global instincts.`, `test_load_all_global_only()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 182`** (2 nodes): `Global fallback should return global instincts for project-only queries.`, `test_load_project_only_global_fallback_loads_global()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 183`** (2 nodes): `No projects should print helpful message.`, `test_cmd_projects_empty_registry()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 184`** (2 nodes): `Promoting nonexistent instinct should fail.`, `test_promote_specific_not_found()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 185`** (2 nodes): `Path-like instinct IDs should be rejected before file writes.`, `test_promote_specific_rejects_invalid_id()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 186`** (2 nodes): `Promoting an instinct that already exists globally should fail.`, `test_promote_specific_already_global()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 187`** (2 nodes): `Promote a project instinct to global with --force.`, `test_promote_specific_success()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 188`** (2 nodes): `Auto-promote with no cross-project instincts should say so.`, `test_promote_auto_no_candidates()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 189`** (2 nodes): `Dry run should list candidates but not write files.`, `test_promote_auto_dry_run()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 190`** (2 nodes): `Auto-promote with force should write global instinct file.`, `test_promote_auto_writes_file()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 191`** (2 nodes): `Single project should return nothing (need 2+).`, `test_find_cross_project_single_project()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CodeHygieneTest` connect `Community 2` to `Community 4`?**
  _High betweenness centrality (0.044) - this node is a cross-community bridge._
- **Why does `FitnessAiService` connect `Community 9` to `Community 6`?**
  _High betweenness centrality (0.030) - this node is a cross-community bridge._
- **Why does `Profile` connect `Community 11` to `Community 10`?**
  _High betweenness centrality (0.028) - this node is a cross-community bridge._
- **What connects `Validate and resolve a file path, guarding against path traversal.      Raises`, `Validate instinct IDs before using them in filenames.`, `Quote a string for safe YAML frontmatter serialization.      Uses double quote` to the rest of the system?**
  _100 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.07205387205387205 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.06868686868686869 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.06533776301218161 - nodes in this community are weakly interconnected._