# Graph Report - .  (2026-08-09)

## Corpus Check
- Large corpus: 569 files · ~318 389 words. Semantic extraction will be expensive (many Claude tokens). Consider running on a subfolder, or use --no-semantic to run AST-only.

## Summary
- 2134 nodes · 3874 edges · 184 communities detected
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 69 edges (avg confidence: 0.5)
- Token cost: 0 input · 0 output
- Edge kinds: method: 1020 · MODIFIES: 950 · calls: 573 · contains: 491 · imports: 255 · PARENT_OF: 145 · ON_BRANCH: 138 · rationale_for: 97 · uses: 69 · inherits: 53 · implements: 49 · imports_from: 19 · references: 15


## Input Scope
- Requested: auto
- Resolved: committed (source: default-auto)
- Included files: 569 · Candidates: 849
- Excluded: 64 untracked · 1450 ignored · 30 sensitive · 0 missing committed
- Recommendation: Use --scope all or graphify.yaml inputs.corpus for a knowledge-base folder.

## Graph Freshness
- Built from Git commit: `ffb05aa`
- Compare this hash to `git rev-parse HEAD` before trusting freshness-sensitive graph output.
## God Nodes (most connected - your core abstractions)
1. `CodeHygieneTest` - 43 edges
2. `FitnessAiService` - 37 edges
3. `Profile` - 28 edges
4. `User` - 22 edges
5. `JefitCsvParserAdapter` - 22 edges
6. `ObservationEvent` - 21 edges
7. `GlobalExceptionHandler` - 21 edges
8. `_make_project()` - 20 edges
9. `TelegramBotService` - 19 edges
10. `WorkoutPersistenceAdapterPostgresIntegrationTest` - 18 edges

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
Nodes (54): _ensure_global_dirs(), _validate_file_path(), _validate_instinct_id(), _yaml_quote(), detect_project(), _update_registry(), load_registry(), parse_instinct_file() (+46 more)

### Community 17 - "Community 17"
Cohesion: 0.07
Nodes (1): Tests for continuous-learning-v2 instinct-cli.py  Covers:   - parse_instinct_

### Community 165 - "Community 165"
Cohesion: 1.00
Nodes (2): project_tree(), Create a realistic project directory tree for testing.

### Community 164 - "Community 164"
Cohesion: 1.00
Nodes (2): patch_globals(), Patch module-level globals to use tmp_path-based directories.

### Community 27 - "Community 27"
Cohesion: 0.10
Nodes (21): _make_project(), test_load_all_project_and_global(), test_load_all_project_overrides_global(), test_load_project_only_excludes_global(), test_load_all_empty(), test_cmd_status_no_instincts(), test_cmd_status_with_instincts(), test_cmd_status_returns_int() (+13 more)

### Community 166 - "Community 166"
Cohesion: 1.00
Nodes (2): test_parse_no_id_skipped(), Instincts without an 'id' field should be silently dropped.

### Community 167 - "Community 167"
Cohesion: 1.00
Nodes (2): test_validate_home_expansion(), Tilde expansion should work.

### Community 168 - "Community 168"
Cohesion: 1.00
Nodes (2): test_validate_relative_path(), Relative paths should be resolved.

### Community 169 - "Community 169"
Cohesion: 1.00
Nodes (2): test_detect_project_global_fallback(), When no git and no env var, should return global project.

### Community 170 - "Community 170"
Cohesion: 1.00
Nodes (2): test_detect_project_from_env(), CLAUDE_PROJECT_DIR env var should be used as project root.

### Community 171 - "Community 171"
Cohesion: 1.00
Nodes (2): test_detect_project_git_timeout(), Git timeout should fall through to global.

### Community 172 - "Community 172"
Cohesion: 1.00
Nodes (2): test_detect_project_creates_directories(), detect_project should create the project dir structure.

### Community 173 - "Community 173"
Cohesion: 1.00
Nodes (2): test_load_annotates_metadata(), Loaded instincts should have _source_file, _source_type, _scope_label.

### Community 174 - "Community 174"
Cohesion: 1.00
Nodes (2): test_load_defaults_scope_from_label(), If an instinct has no 'scope' in frontmatter, it should default to scope_label.

### Community 175 - "Community 175"
Cohesion: 1.00
Nodes (2): test_load_preserves_explicit_scope(), If frontmatter has explicit scope, it should be preserved.

### Community 176 - "Community 176"
Cohesion: 1.00
Nodes (2): test_load_handles_corrupt_file(), Corrupt YAML files should be warned about but not crash.

### Community 177 - "Community 177"
Cohesion: 1.00
Nodes (2): test_load_all_global_only(), Global project should only load global instincts.

### Community 178 - "Community 178"
Cohesion: 1.00
Nodes (2): test_load_project_only_global_fallback_loads_global(), Global fallback should return global instincts for project-only queries.

### Community 179 - "Community 179"
Cohesion: 1.00
Nodes (2): test_cmd_projects_empty_registry(), No projects should print helpful message.

### Community 180 - "Community 180"
Cohesion: 1.00
Nodes (2): test_promote_specific_not_found(), Promoting nonexistent instinct should fail.

### Community 181 - "Community 181"
Cohesion: 1.00
Nodes (2): test_promote_specific_rejects_invalid_id(), Path-like instinct IDs should be rejected before file writes.

### Community 182 - "Community 182"
Cohesion: 1.00
Nodes (2): test_promote_specific_already_global(), Promoting an instinct that already exists globally should fail.

### Community 183 - "Community 183"
Cohesion: 1.00
Nodes (2): test_promote_specific_success(), Promote a project instinct to global with --force.

### Community 184 - "Community 184"
Cohesion: 1.00
Nodes (2): test_promote_auto_no_candidates(), Auto-promote with no cross-project instincts should say so.

### Community 185 - "Community 185"
Cohesion: 1.00
Nodes (2): test_promote_auto_dry_run(), Dry run should list candidates but not write files.

### Community 186 - "Community 186"
Cohesion: 1.00
Nodes (2): test_promote_auto_writes_file(), Auto-promote with force should write global instinct file.

### Community 187 - "Community 187"
Cohesion: 1.00
Nodes (2): test_find_cross_project_single_project(), Single project should return nothing (need 2+).

### Community 46 - "Community 46"
Cohesion: 0.16
Nodes (8): CLI entry point for skill-comply., generate_spec(), Generate compliance specs from skill files using LLM., Generate a compliance spec from a skill/rule file.      Calls claude -p with t, extract_yaml(), Shared utilities for skill-comply scripts., Extract YAML from LLM output, stripping markdown fences if present., 2b1a12a chore: baseline before security hardening

### Community 26 - "Community 26"
Cohesion: 0.23
Nodes (19): classify_events(), _parse_classification(), Classify tool calls against compliance steps using LLM., Classify which tool calls match which compliance steps.      Returns {step_id:, Parse LLM classification output into {step_id: [event_indices]}., StepResult, ComplianceResult, _check_temporal_order() (+11 more)

### Community 100 - "Community 100"
Cohesion: 0.33
Nodes (6): Detector, parse_trace(), parse_spec(), Parse observation traces (JSONL) and compliance specs (YAML)., Parse a JSONL observation trace file into sorted events., Parse a YAML compliance spec file.

### Community 138 - "Community 138"
Cohesion: 0.80
Nodes (4): generate_report(), _overall_compliance(), _step_compliance_rate(), _steps_to_promote()

### Community 40 - "Community 40"
Cohesion: 0.22
Nodes (14): ScenarioRun, run_scenario(), _safe_sandbox_dir(), _setup_sandbox(), _parse_stream_json(), Run scenarios via claude -p and parse tool calls from stream-json output., Execute a scenario and extract tool calls from stream-json output., Sanitize scenario ID and ensure path stays within sandbox base. (+6 more)

### Community 55 - "Community 55"
Cohesion: 0.15
Nodes (5): _mock_compliant_classification(), _mock_noncompliant_classification(), TestGradeEdgeCases, Simulate LLM correctly classifying a compliant trace., Simulate LLM classifying a noncompliant trace (impl before test).

### Community 103 - "Community 103"
Cohesion: 0.29
Nodes (1): TestGradeCompliant

### Community 102 - "Community 102"
Cohesion: 0.29
Nodes (2): TestGradeNoncompliant, write_test has before_step=write_impl, but test is written AFTER impl.

### Community 104 - "Community 104"
Cohesion: 0.29
Nodes (1): TestParseTrace

### Community 120 - "Community 120"
Cohesion: 0.33
Nodes (1): TestParseSpec

### Community 30 - "Community 30"
Cohesion: 0.15
Nodes (19): default_output_dir(), ensure_private_dir(), parse_args(), log(), append_event(), write_pid(), cleanup_pid(), is_fatal_error() (+11 more)

### Community 29 - "Community 29"
Cohesion: 0.13
Nodes (8): FitnessAppApplication, AiClientConfig, executionOrDefaults(), defaults(), 3d3abaa renamed, e469d36 Initial commit, f1d2018 AI start, f2f3015 Merge branch 'refs/heads/PROD_Add-ai-base' into PROD

### Community 124 - "Community 124"
Cohesion: 0.70
Nodes (1): AiController

### Community 62 - "Community 62"
Cohesion: 0.24
Nodes (6): AiInsightEntity, ai_insights, users, 618a93e feature: add transcatinal outbox, dbd1c70 HUGE REFACTORING OF AI MODULE, fc44122 feature: add init events

### Community 41 - "Community 41"
Cohesion: 0.18
Nodes (3): AiTodayInsightResponse, AiControllerTest, badd79d fix: type controller responses

### Community 12 - "Community 12"
Cohesion: 0.13
Nodes (11): MoeOrchestrator, AiModelPort, MemoryConfig, user_memory, 03e0965 cleaned up, 2ffe3ae chore: normalize code comments, 31ee683 temp clean, 5896320 feat : added detailed recent insights (+3 more)

### Community 37 - "Community 37"
Cohesion: 0.13
Nodes (5): AiInsightRepository, AiInsightPort, AiContextService, MemoryService, MemoryQueryUseCase

### Community 65 - "Community 65"
Cohesion: 0.25
Nodes (6): JpaRepository, ConversationStateRepository, ConversationStateEntity, WorkoutSetJpaRepository, WorkoutSetJpaEntity, UserRepositorySignatureTest

### Community 13 - "Community 13"
Cohesion: 0.10
Nodes (10): AiInsightEntity, NutritionQueryUseCase, WorkoutDailyApi, WorkoutDailyApi, WorkoutCardioJpaEntity, workout_cardio, AiPromptRendererTest, 20eb08b feat: include workout context in daily insights (+2 more)

### Community 67 - "Community 67"
Cohesion: 0.18
Nodes (4): Long, ProfileJpaRepositoryImpl, WeightHistoryJpaRepository, TelegramUserRepository

### Community 125 - "Community 125"
Cohesion: 0.50
Nodes (1): AiPromptRenderer

### Community 5 - "Community 5"
Cohesion: 0.08
Nodes (15): MonthlyReportController, NutritionMonthlyApi, NutritionMonthlyApi, WorkoutMonthlyApi, WorkoutMonthlyApi, 20ff72a Merge pull request #22 from DmitriyGrachev/PROD_new_feat_month, 224396b HUGE REFACTORING OF AI MODULE + adapters, 3815095 Merge remote-tracking branch 'origin/PROD' into PROD (+7 more)

### Community 7 - "Community 7"
Cohesion: 0.17
Nodes (1): FitnessAiService

### Community 75 - "Community 75"
Cohesion: 0.25
Nodes (5): RateLimitInterceptor, HandlerInterceptor, CurrentUserApi, WorkoutImportController, c4f8830 Fail closed when AI user cannot be resolved

### Community 107 - "Community 107"
Cohesion: 0.47
Nodes (2): RateLimiterService, AiRateLimitApi

### Community 16 - "Community 16"
Cohesion: 0.09
Nodes (10): WebMvcConfig, WebMvcConfigurer, WeeklyReportController, UserApi, NutritionWeeklyApi, WorkoutWeeklyApi, RateLimitInterceptorTest, 02bccc7 fix: add ai router cooldown (+2 more)

### Community 93 - "Community 93"
Cohesion: 0.48
Nodes (1): SmartAiRouter

### Community 52 - "Community 52"
Cohesion: 0.24
Nodes (3): GeminiAdapter, AiModelPort, OpenRouterAdapter

### Community 60 - "Community 60"
Cohesion: 0.24
Nodes (1): AiSafetyService

### Community 101 - "Community 101"
Cohesion: 0.52
Nodes (1): DailyInsightService

### Community 153 - "Community 153"
Cohesion: 0.83
Nodes (1): DailyInsightSnapshotService

### Community 155 - "Community 155"
Cohesion: 0.67
Nodes (1): TelegramAskAiService

### Community 22 - "Community 22"
Cohesion: 0.10
Nodes (10): AiAuthException, ExternalApiException, AiInvalidRequestException, AiUnavailableException, ExternalServiceUnavailableException, ExternalApiException, RuntimeException, ExternalServiceUnavailableException (+2 more)

### Community 158 - "Community 158"
Cohesion: 0.67
Nodes (1): MonthlyReportOrchestrator

### Community 159 - "Community 159"
Cohesion: 1.00
Nodes (1): MonthlyReportTransactionService

### Community 23 - "Community 23"
Cohesion: 0.11
Nodes (8): WeeklyReportOrchestrator, UserApi, NutritionWeeklyApi, WorkoutWeeklyApi, ActuatorEndpoints, OpenApiEndpoint, 6d9d313 fix: resolve modulith cycles, ba3d356 Harden privacy logging and test hygiene

### Community 160 - "Community 160"
Cohesion: 1.00
Nodes (1): WeeklyReportTransactionService

### Community 157 - "Community 157"
Cohesion: 0.67
Nodes (1): AiRateLimitApi

### Community 56 - "Community 56"
Cohesion: 0.17
Nodes (4): TelegramNoteListener, TelegramWeightListener, 8112188 fix : utf, and added missing files, ab7b2cc fix : ai props

### Community 20 - "Community 20"
Cohesion: 0.12
Nodes (9): NutritionCommandPort, SyncNutritionUseCase, NutritionServicePrivacyLoggingTest, WeightHistoryRepositoryTest, 426f943 Fix nutrition workout AI insight workflows, 64dd819 fix: reduce nutrition sync churn, e0ccccb fix: mark nutrition scheduler constructor for injection, eab64da fix: track nutrition sync freshness hashes (+1 more)

### Community 35 - "Community 35"
Cohesion: 0.19
Nodes (9): WorkoutImportedEvent(), datesBetween(), WorkoutParserPort, WorkoutPersistencePort, WorkoutImportService, ImportWorkoutUseCase, 4d30761 test: keep postgres fixtures within user column limits, 9d2ce50 fix: stabilize ingestion-driven daily insights (+1 more)

### Community 9 - "Community 9"
Cohesion: 0.07
Nodes (11): ErrorCode, TelegramAiResponseListener, TelegramBotConfig, AuthRateLimitWebTest, AuthEndpoints, UserAuthorityTest, 25c83e7 fix : profile for test, 2e37542 chore: baseline before error contract (+3 more)

### Community 11 - "Community 11"
Cohesion: 0.11
Nodes (12): LoginUseCase, RegisterUserPort, UserPersistencePort, WebConfig, WorkoutJpaEntity, WorkoutSummaryWeeklyDto, 3b09783 rafactoring: Created seperate auth modul for Security and User, 42a3900 Merge pull request #17 from DmitriyGrachev/PROD_refactor-auth-module (+4 more)

### Community 144 - "Community 144"
Cohesion: 0.50
Nodes (1): CurrentUserApi

### Community 163 - "Community 163"
Cohesion: 0.67
Nodes (1): AuthController

### Community 70 - "Community 70"
Cohesion: 0.20
Nodes (3): UserDataLifecycleController, UserDataLifecycleService, UserDataLifecycleUseCase

### Community 2 - "Community 2"
Cohesion: 0.05
Nodes (14): UserNoteController, UserNotePersistenceAdapter, UserNotePersistencePort, UserNoteUseCase, UserNotePersistencePort, UserNoteService, UserNoteUseCase, user_notes (+6 more)

### Community 115 - "Community 115"
Cohesion: 0.40
Nodes (2): UserAdapter, UserPort

### Community 21 - "Community 21"
Cohesion: 0.10
Nodes (11): UserAuthenticationAdapter, UserAuthenticationPort, userdetails, UserAuthenticationPort, LoginService, LoginUseCase, UserDetailsService, org (+3 more)

### Community 116 - "Community 116"
Cohesion: 0.40
Nodes (2): UserNoteJpaRepository, UserNote

### Community 71 - "Community 71"
Cohesion: 0.20
Nodes (5): UserPersistenceAdapterTest, 0d11de1 docs: design P0 data foundation phase, 1b6b04a fix: guard nutrition month reconciliation, 59e8a51 fix: reject malformed FatSecret errors, b4d9ef2 fix(auth): canonicalize registration and handle duplicate races

### Community 1 - "Community 1"
Cohesion: 0.07
Nodes (9): UserPersistenceAdapter, UserPersistencePort, UserAndOwnershipSchemaIntegrationTest, AbstractPostgresIntegrationTest, UserNoteForeignKeyIntegrationTest, RegisterServiceTest, MemoryPgVectorIntegrationTest, NutritionPersistenceAdapterPostgresIntegrationTest (+1 more)

### Community 38 - "Community 38"
Cohesion: 0.13
Nodes (1): UserNote

### Community 28 - "Community 28"
Cohesion: 0.10
Nodes (1): User

### Community 81 - "Community 81"
Cohesion: 0.22
Nodes (1): UserRepository

### Community 3 - "Community 3"
Cohesion: 0.08
Nodes (22): User, TelegramNotificationListener, ConversationStateUseCase, TelegramProperties, ConversationStateEntity, TelegramUserEntity, TelegramUserEntity, 00af208 fix: limit telegram link attempts (+14 more)

### Community 57 - "Community 57"
Cohesion: 0.18
Nodes (4): telegram_link_codes, users, telegram_delivery_outbox, 084756d feat: add durable job tracking, telegram link/outbox, gdpr lifecycle, ai safety, and time policy

### Community 33 - "Community 33"
Cohesion: 0.11
Nodes (7): UserPort, MemoryCleanupPort, MemoryCleanupScheduler, AbstractPostgresIntegrationTest, 1401f78 chore: add Codex project guidance, 19c38a8 fix: encrypt fatsecret tokens, 641c78f fix: clean up expired user memory

### Community 133 - "Community 133"
Cohesion: 0.40
Nodes (1): UserDataLifecycleUseCase

### Community 140 - "Community 140"
Cohesion: 0.60
Nodes (1): CurrentUserService

### Community 151 - "Community 151"
Cohesion: 0.67
Nodes (2): RegisterService, RegisterUserPort

### Community 119 - "Community 119"
Cohesion: 0.47
Nodes (1): UserTimeService

### Community 31 - "Community 31"
Cohesion: 0.11
Nodes (5): AppTimeConfig, ApiErrorResponseWriter, Clock, UserDataLifecycleServiceTest, WorkoutQueryUseCaseTest

### Community 109 - "Community 109"
Cohesion: 0.47
Nodes (1): SecurityConfig

### Community 85 - "Community 85"
Cohesion: 0.39
Nodes (2): AuthRateLimitFilter, OncePerRequestFilter

### Community 121 - "Community 121"
Cohesion: 0.53
Nodes (1): JwtCore

### Community 24 - "Community 24"
Cohesion: 0.18
Nodes (1): GlobalExceptionHandler

### Community 59 - "Community 59"
Cohesion: 0.17
Nodes (1): DurableJobUseCase

### Community 141 - "Community 141"
Cohesion: 0.50
Nodes (1): DurableJobController

### Community 43 - "Community 43"
Cohesion: 0.17
Nodes (2): DurableJobService, DurableJobUseCase

### Community 154 - "Community 154"
Cohesion: 0.67
Nodes (1): DurableJobWorker

### Community 48 - "Community 48"
Cohesion: 0.16
Nodes (5): JdbcMemoryCleanupAdapter, MemoryCleanupPort, MemoryCleanupService, MemoryCleanupServiceTest, FakeMemoryCleanupPort

### Community 147 - "Community 147"
Cohesion: 0.50
Nodes (1): MemoryQueryUseCase

### Community 82 - "Community 82"
Cohesion: 0.44
Nodes (1): MemoryEventListener

### Community 49 - "Community 49"
Cohesion: 0.14
Nodes (1): DailyMacrosDto

### Community 14 - "Community 14"
Cohesion: 0.08
Nodes (7): NutritionController, NutritionService, ConnectFatSecretUseCase, NutritionControllerSyncTest, NutritionControllerValidationTest, 0543b9f fix: return sync completion status, 1f9132d fix: validate nutrition date range

### Community 18 - "Community 18"
Cohesion: 0.13
Nodes (8): TestController, Profile, WeightHistory, ProfileUseCase, weight_history, users, 5e01166 Merge pull request #24 from DmitriyGrachev/feature/user-context-enhancement, fa3fa65 feat : added user_note, weight, profile(empty)

### Community 114 - "Community 114"
Cohesion: 0.40
Nodes (1): NutritionJdbcQueryAdapter

### Community 39 - "Community 39"
Cohesion: 0.21
Nodes (1): NutritionPersistenceAdapter

### Community 19 - "Community 19"
Cohesion: 0.09
Nodes (8): ProfileJpaRepository, ProfileUseCase, WeightHistoryUseCase, UserTimeServiceTest, DateRangeTest, TelegramWeightListenerPrivacyLoggingTest, NutritionDayStatusTest, e8c758b tests: add unit and integration test coverage for durable jobs, time policy, telegram link, and gdpr lifecycle

### Community 117 - "Community 117"
Cohesion: 0.40
Nodes (1): WeightHistoryRepository

### Community 15 - "Community 15"
Cohesion: 0.07
Nodes (1): Profile

### Community 47 - "Community 47"
Cohesion: 0.14
Nodes (1): WeightHistory

### Community 10 - "Community 10"
Cohesion: 0.07
Nodes (9): NutritionQueryUseCase, SyncNutritionUseCase, CaffeineCacheConfig, TempWorkout, TempExercise, ImportWorkoutUseCase, WorkoutPersistencePort, 12c65d2 made indopot (+1 more)

### Community 134 - "Community 134"
Cohesion: 0.40
Nodes (1): WeightHistoryUseCase

### Community 98 - "Community 98"
Cohesion: 0.29
Nodes (1): NutritionCommandPort

### Community 87 - "Community 87"
Cohesion: 0.39
Nodes (1): NutritionSyncScheduler

### Community 156 - "Community 156"
Cohesion: 0.50
Nodes (1): TimeEntryUtil

### Community 77 - "Community 77"
Cohesion: 0.25
Nodes (2): NutritionDay(), safeEntries()

### Community 113 - "Community 113"
Cohesion: 0.40
Nodes (2): TelegramUpdateHandler, TelegramLongPollingBot

### Community 142 - "Community 142"
Cohesion: 0.40
Nodes (1): TelegramLinkController

### Community 112 - "Community 112"
Cohesion: 0.33
Nodes (1): ConversationStateUseCase

### Community 139 - "Community 139"
Cohesion: 0.50
Nodes (1): ConversationStateService

### Community 32 - "Community 32"
Cohesion: 0.21
Nodes (1): TelegramBotService

### Community 74 - "Community 74"
Cohesion: 0.27
Nodes (1): TelegramLinkCodeManager

### Community 84 - "Community 84"
Cohesion: 0.22
Nodes (1): TelegramMessages

### Community 108 - "Community 108"
Cohesion: 0.33
Nodes (3): TelegramOutboxWorker, 741a6e9 config: update build setup, graphify knowledge graph, and token filter logger, ba73c2f feat: add durable job worker, telegram outbox worker, gdpr controller, and fatsecret syncer

### Community 8 - "Community 8"
Cohesion: 0.06
Nodes (8): AskCommandHandler, CommandHandler, LinkCommandHandler, NoteCommandHandler, StartCommandHandler, TestGenerateHandler, TodayCommandHandler, WeightCommandHandler

### Community 130 - "Community 130"
Cohesion: 0.40
Nodes (1): CommandHandler

### Community 90 - "Community 90"
Cohesion: 0.25
Nodes (1): WorkoutDailyStatsDto

### Community 91 - "Community 91"
Cohesion: 0.25
Nodes (1): WorkoutWeeklyStatsDto

### Community 66 - "Community 66"
Cohesion: 0.27
Nodes (4): WorkoutAnalyticController, WorkoutQueryUseCase, WorkoutAnalyticControllerTest, 746f016 fix: add workout analytic path alias

### Community 4 - "Community 4"
Cohesion: 0.08
Nodes (39): 028d224 config: configure global UTC clock bean and align dev runtime properties, 08c2c56 fix: expose secured actuator endpoints, 12e4208 fix: update spring boot tomcat line, 12f46f9 conf : gitignore file, 14d36eb fix: harden security backlog items, 163791c ci: add maven baseline workflow, 17bbfd1 fix: ignore Telegram non-private updates, 17e3f61 fix: remove stale telegram link service (+31 more)

### Community 73 - "Community 73"
Cohesion: 0.33
Nodes (1): WorkoutJdbcQueryAdapter

### Community 25 - "Community 25"
Cohesion: 0.29
Nodes (1): JefitCsvParserAdapter

### Community 45 - "Community 45"
Cohesion: 0.20
Nodes (7): WorkoutCardioJpaRepository, WorkoutCardioJpaEntity, 1c62725 fix: scope jefit exercise lookup by workout, 3bdb197 docs: plan ingestion idempotency work, 50d2833 fix: make workout import idempotent, 8b45fec fix: make workout persistence save transactional, e8433de fix: avoid duplicate workout set deletes

### Community 80 - "Community 80"
Cohesion: 0.44
Nodes (1): WorkoutPersistenceAdapter

### Community 53 - "Community 53"
Cohesion: 0.19
Nodes (9): WorkoutExerciseJpaEntity, WorkoutSetJpaEntity, WorkoutSummaryDto, 1f2d197 Stabilize workout JDBC query tests, 4236495 bug: Fixed date in workouts, 625a7ac featur: add workout weekly stat, 688f01d feat: base for analytics module, 69f0ed7 feat: Added mini CQRS bug: Fixed timestamp and localdatetime in parsing issue (+1 more)

### Community 99 - "Community 99"
Cohesion: 0.33
Nodes (3): WorkoutExerciseJpaRepository, WorkoutExerciseJpaEntity, WorkoutExerciseJpaEntityMappingTest

### Community 152 - "Community 152"
Cohesion: 0.67
Nodes (2): WorkoutJpaRepository, WorkoutJpaEntity

### Community 135 - "Community 135"
Cohesion: 0.40
Nodes (1): WorkoutQueryUseCase

### Community 150 - "Community 150"
Cohesion: 0.50
Nodes (1): WorkoutParserPort

### Community 161 - "Community 161"
Cohesion: 1.00
Nodes (2): user_notes, users

### Community 148 - "Community 148"
Cohesion: 1.00
Nodes (3): users, workout, workout_cardio

### Community 162 - "Community 162"
Cohesion: 1.00
Nodes (2): durable_jobs, users

### Community 68 - "Community 68"
Cohesion: 0.29
Nodes (10): users, user_roles, fatsecret_day, fatsecret_food, fatsecret_connection, profile, workout, workout_exercises (+2 more)

### Community 137 - "Community 137"
Cohesion: 0.50
Nodes (4): telegram_users, users, conversation_state, conversation_history

### Community 6 - "Community 6"
Cohesion: 0.07
Nodes (1): CodeHygieneTest

### Community 42 - "Community 42"
Cohesion: 0.13
Nodes (1): FitnessAiServiceDelegationTest

### Community 63 - "Community 63"
Cohesion: 0.45
Nodes (1): FitnessAiServiceReportFreshnessTest

### Community 126 - "Community 126"
Cohesion: 0.40
Nodes (1): GeminiAdapterTest

### Community 51 - "Community 51"
Cohesion: 0.21
Nodes (5): MoeOrchestratorTest, DailyInsight, QuickAnalysis, WeeklyReport, MonthlyReport

### Community 92 - "Community 92"
Cohesion: 0.43
Nodes (1): OpenRouterAdapterTest

### Community 127 - "Community 127"
Cohesion: 0.40
Nodes (1): RateLimiterServiceTest

### Community 34 - "Community 34"
Cohesion: 0.16
Nodes (2): SmartAiRouterTest, MutableClock

### Community 44 - "Community 44"
Cohesion: 0.23
Nodes (1): DailyInsightServiceTest

### Community 61 - "Community 61"
Cohesion: 0.32
Nodes (1): DailyInsightSnapshotServiceTest

### Community 88 - "Community 88"
Cohesion: 0.39
Nodes (1): TelegramAskAiServiceTest

### Community 143 - "Community 143"
Cohesion: 0.67
Nodes (1): ActuatorSecurityWebTest

### Community 64 - "Community 64"
Cohesion: 0.18
Nodes (1): AuthControllerTest

### Community 145 - "Community 145"
Cohesion: 0.83
Nodes (1): OpenApiSecurityWebTest

### Community 94 - "Community 94"
Cohesion: 0.43
Nodes (1): SecurityConfigWebTest

### Community 128 - "Community 128"
Cohesion: 0.40
Nodes (1): TestEndpoints

### Community 76 - "Community 76"
Cohesion: 0.53
Nodes (1): SecurityProtectedEndpointsWebTest

### Community 54 - "Community 54"
Cohesion: 0.15
Nodes (2): ApiErrorHandlerWebTest, ErrorEndpoints

### Community 123 - "Community 123"
Cohesion: 0.33
Nodes (1): WorkoutJdbcQueryAdapterSqlTest

### Community 86 - "Community 86"
Cohesion: 0.25
Nodes (1): DurableJobServiceTest

### Community 136 - "Community 136"
Cohesion: 0.40
Nodes (1): MemoryEventListenerTest

### Community 72 - "Community 72"
Cohesion: 0.42
Nodes (1): MemoryServiceTest

### Community 149 - "Community 149"
Cohesion: 0.50
Nodes (1): ModuleArchitectureTest

### Community 78 - "Community 78"
Cohesion: 0.39
Nodes (1): NutritionMonthlyApiTest

### Community 79 - "Community 79"
Cohesion: 0.36
Nodes (1): NutritionPersistenceAdapterIdempotencyTest

### Community 69 - "Community 69"
Cohesion: 0.29
Nodes (1): NutritionServiceSyncWorkflowTest

### Community 83 - "Community 83"
Cohesion: 0.22
Nodes (1): NutritionSyncSchedulerTest

### Community 97 - "Community 97"
Cohesion: 0.43
Nodes (1): TelegramUpdateHandlerPrivacyLoggingTest

### Community 122 - "Community 122"
Cohesion: 0.33
Nodes (1): TelegramLinkControllerTest

### Community 89 - "Community 89"
Cohesion: 0.32
Nodes (1): TelegramBotServiceTest

### Community 111 - "Community 111"
Cohesion: 0.73
Nodes (1): AskCommandHandlerTest

### Community 95 - "Community 95"
Cohesion: 0.43
Nodes (1): LinkCommandHandlerTest

### Community 96 - "Community 96"
Cohesion: 0.52
Nodes (1): NoteCommandHandlerTest

### Community 131 - "Community 131"
Cohesion: 0.50
Nodes (1): StartCommandHandlerTest

### Community 132 - "Community 132"
Cohesion: 0.60
Nodes (1): TodayCommandHandlerTest

### Community 58 - "Community 58"
Cohesion: 0.30
Nodes (1): WeightCommandHandlerTest

### Community 105 - "Community 105"
Cohesion: 0.48
Nodes (1): JefitCsvParserAdapterTest

### Community 106 - "Community 106"
Cohesion: 0.52
Nodes (1): WorkoutImportServiceTest

### Community 50 - "Community 50"
Cohesion: 0.30
Nodes (1): WorkoutPersistenceAdapterTest

### Community 36 - "Community 36"
Cohesion: 0.21
Nodes (1): WorkoutPersistenceAdapterPostgresIntegrationTest

## Knowledge Gaps
- **100 isolated node(s):** `Validate and resolve a file path, guarding against path traversal.      Raises`, `Validate instinct IDs before using them in filenames.`, `Quote a string for safe YAML frontmatter serialization.      Uses double quote`, `Detect current project context. Returns dict with id, name, root, project_dir.`, `Update the projects.json registry.      Uses file locking (where available) to` (+95 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 17`** (1 nodes): `Tests for continuous-learning-v2 instinct-cli.py  Covers:   - parse_instinct_`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 165`** (2 nodes): `project_tree()`, `Create a realistic project directory tree for testing.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 164`** (2 nodes): `patch_globals()`, `Patch module-level globals to use tmp_path-based directories.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 166`** (2 nodes): `test_parse_no_id_skipped()`, `Instincts without an 'id' field should be silently dropped.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 167`** (2 nodes): `test_validate_home_expansion()`, `Tilde expansion should work.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 168`** (2 nodes): `test_validate_relative_path()`, `Relative paths should be resolved.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 169`** (2 nodes): `test_detect_project_global_fallback()`, `When no git and no env var, should return global project.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 170`** (2 nodes): `test_detect_project_from_env()`, `CLAUDE_PROJECT_DIR env var should be used as project root.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 171`** (2 nodes): `test_detect_project_git_timeout()`, `Git timeout should fall through to global.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 172`** (2 nodes): `test_detect_project_creates_directories()`, `detect_project should create the project dir structure.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 173`** (2 nodes): `test_load_annotates_metadata()`, `Loaded instincts should have _source_file, _source_type, _scope_label.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 174`** (2 nodes): `test_load_defaults_scope_from_label()`, `If an instinct has no 'scope' in frontmatter, it should default to scope_label.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 175`** (2 nodes): `test_load_preserves_explicit_scope()`, `If frontmatter has explicit scope, it should be preserved.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 176`** (2 nodes): `test_load_handles_corrupt_file()`, `Corrupt YAML files should be warned about but not crash.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 177`** (2 nodes): `test_load_all_global_only()`, `Global project should only load global instincts.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 178`** (2 nodes): `test_load_project_only_global_fallback_loads_global()`, `Global fallback should return global instincts for project-only queries.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 179`** (2 nodes): `test_cmd_projects_empty_registry()`, `No projects should print helpful message.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 180`** (2 nodes): `test_promote_specific_not_found()`, `Promoting nonexistent instinct should fail.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 181`** (2 nodes): `test_promote_specific_rejects_invalid_id()`, `Path-like instinct IDs should be rejected before file writes.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 182`** (2 nodes): `test_promote_specific_already_global()`, `Promoting an instinct that already exists globally should fail.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 183`** (2 nodes): `test_promote_specific_success()`, `Promote a project instinct to global with --force.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 184`** (2 nodes): `test_promote_auto_no_candidates()`, `Auto-promote with no cross-project instincts should say so.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 185`** (2 nodes): `test_promote_auto_dry_run()`, `Dry run should list candidates but not write files.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 186`** (2 nodes): `test_promote_auto_writes_file()`, `Auto-promote with force should write global instinct file.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 187`** (2 nodes): `test_find_cross_project_single_project()`, `Single project should return nothing (need 2+).`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 103`** (1 nodes): `TestGradeCompliant`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 102`** (2 nodes): `TestGradeNoncompliant`, `write_test has before_step=write_impl, but test is written AFTER impl.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 104`** (1 nodes): `TestParseTrace`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 120`** (1 nodes): `TestParseSpec`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 124`** (1 nodes): `AiController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 125`** (1 nodes): `AiPromptRenderer`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 7`** (1 nodes): `FitnessAiService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 107`** (2 nodes): `RateLimiterService`, `AiRateLimitApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 93`** (1 nodes): `SmartAiRouter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 60`** (1 nodes): `AiSafetyService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 101`** (1 nodes): `DailyInsightService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 153`** (1 nodes): `DailyInsightSnapshotService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 155`** (1 nodes): `TelegramAskAiService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 158`** (1 nodes): `MonthlyReportOrchestrator`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 159`** (1 nodes): `MonthlyReportTransactionService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 160`** (1 nodes): `WeeklyReportTransactionService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 157`** (1 nodes): `AiRateLimitApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 144`** (1 nodes): `CurrentUserApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 163`** (1 nodes): `AuthController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 115`** (2 nodes): `UserAdapter`, `UserPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 116`** (2 nodes): `UserNoteJpaRepository`, `UserNote`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 38`** (1 nodes): `UserNote`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 28`** (1 nodes): `User`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 81`** (1 nodes): `UserRepository`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 133`** (1 nodes): `UserDataLifecycleUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 140`** (1 nodes): `CurrentUserService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 151`** (2 nodes): `RegisterService`, `RegisterUserPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 119`** (1 nodes): `UserTimeService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 109`** (1 nodes): `SecurityConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 85`** (2 nodes): `AuthRateLimitFilter`, `OncePerRequestFilter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 121`** (1 nodes): `JwtCore`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 24`** (1 nodes): `GlobalExceptionHandler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 59`** (1 nodes): `DurableJobUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 141`** (1 nodes): `DurableJobController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 43`** (2 nodes): `DurableJobService`, `DurableJobUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 154`** (1 nodes): `DurableJobWorker`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 147`** (1 nodes): `MemoryQueryUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 82`** (1 nodes): `MemoryEventListener`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 49`** (1 nodes): `DailyMacrosDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 114`** (1 nodes): `NutritionJdbcQueryAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 39`** (1 nodes): `NutritionPersistenceAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 117`** (1 nodes): `WeightHistoryRepository`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 15`** (1 nodes): `Profile`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 47`** (1 nodes): `WeightHistory`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 134`** (1 nodes): `WeightHistoryUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 98`** (1 nodes): `NutritionCommandPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 87`** (1 nodes): `NutritionSyncScheduler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 156`** (1 nodes): `TimeEntryUtil`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 77`** (2 nodes): `NutritionDay()`, `safeEntries()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 113`** (2 nodes): `TelegramUpdateHandler`, `TelegramLongPollingBot`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 142`** (1 nodes): `TelegramLinkController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 112`** (1 nodes): `ConversationStateUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 139`** (1 nodes): `ConversationStateService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 32`** (1 nodes): `TelegramBotService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 74`** (1 nodes): `TelegramLinkCodeManager`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 84`** (1 nodes): `TelegramMessages`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 130`** (1 nodes): `CommandHandler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 90`** (1 nodes): `WorkoutDailyStatsDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 91`** (1 nodes): `WorkoutWeeklyStatsDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 73`** (1 nodes): `WorkoutJdbcQueryAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 25`** (1 nodes): `JefitCsvParserAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 80`** (1 nodes): `WorkoutPersistenceAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 152`** (2 nodes): `WorkoutJpaRepository`, `WorkoutJpaEntity`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 135`** (1 nodes): `WorkoutQueryUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 150`** (1 nodes): `WorkoutParserPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 161`** (2 nodes): `user_notes`, `users`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 162`** (2 nodes): `durable_jobs`, `users`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 6`** (1 nodes): `CodeHygieneTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 42`** (1 nodes): `FitnessAiServiceDelegationTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 63`** (1 nodes): `FitnessAiServiceReportFreshnessTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 126`** (1 nodes): `GeminiAdapterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 92`** (1 nodes): `OpenRouterAdapterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 127`** (1 nodes): `RateLimiterServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 34`** (2 nodes): `SmartAiRouterTest`, `MutableClock`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 44`** (1 nodes): `DailyInsightServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 61`** (1 nodes): `DailyInsightSnapshotServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 88`** (1 nodes): `TelegramAskAiServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 143`** (1 nodes): `ActuatorSecurityWebTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 64`** (1 nodes): `AuthControllerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 145`** (1 nodes): `OpenApiSecurityWebTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 94`** (1 nodes): `SecurityConfigWebTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 128`** (1 nodes): `TestEndpoints`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 76`** (1 nodes): `SecurityProtectedEndpointsWebTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 54`** (2 nodes): `ApiErrorHandlerWebTest`, `ErrorEndpoints`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 123`** (1 nodes): `WorkoutJdbcQueryAdapterSqlTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 86`** (1 nodes): `DurableJobServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 136`** (1 nodes): `MemoryEventListenerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 72`** (1 nodes): `MemoryServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 149`** (1 nodes): `ModuleArchitectureTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 78`** (1 nodes): `NutritionMonthlyApiTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 79`** (1 nodes): `NutritionPersistenceAdapterIdempotencyTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 69`** (1 nodes): `NutritionServiceSyncWorkflowTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 83`** (1 nodes): `NutritionSyncSchedulerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 97`** (1 nodes): `TelegramUpdateHandlerPrivacyLoggingTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 122`** (1 nodes): `TelegramLinkControllerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 89`** (1 nodes): `TelegramBotServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 111`** (1 nodes): `AskCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 95`** (1 nodes): `LinkCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 96`** (1 nodes): `NoteCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 131`** (1 nodes): `StartCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 132`** (1 nodes): `TodayCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 58`** (1 nodes): `WeightCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 105`** (1 nodes): `JefitCsvParserAdapterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 106`** (1 nodes): `WorkoutImportServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 50`** (1 nodes): `WorkoutPersistenceAdapterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 36`** (1 nodes): `WorkoutPersistenceAdapterPostgresIntegrationTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CodeHygieneTest` connect `Community 6` to `Community 4`?**
  _High betweenness centrality (0.039) - this node is a cross-community bridge._
- **Why does `FitnessAiService` connect `Community 7` to `Community 5`?**
  _High betweenness centrality (0.033) - this node is a cross-community bridge._
- **Why does `Profile` connect `Community 15` to `Community 11`?**
  _High betweenness centrality (0.025) - this node is a cross-community bridge._
- **What connects `Validate and resolve a file path, guarding against path traversal.      Raises`, `Validate instinct IDs before using them in filenames.`, `Quote a string for safe YAML frontmatter serialization.      Uses double quote` to the rest of the system?**
  _100 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.07205387205387205 - nodes in this community are weakly interconnected._
- **Should `Community 17` be split into smaller, more focused modules?**
  _Cohesion score 0.07407407407407407 - nodes in this community are weakly interconnected._
- **Should `Community 27` be split into smaller, more focused modules?**
  _Cohesion score 0.09523809523809523 - nodes in this community are weakly interconnected._