# Graph Report - .  (2026-08-10)

## Corpus Check
- Large corpus: 643 files · ~335 212 words. Semantic extraction will be expensive (many Claude tokens). Consider running on a subfolder, or use --no-semantic to run AST-only.

## Summary
- 2436 nodes · 4484 edges · 224 communities detected
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 69 edges (avg confidence: 0.5)
- Token cost: 0 input · 0 output
- Edge kinds: method: 1212 · MODIFIES: 1086 · calls: 710 · contains: 538 · imports: 326 · PARENT_OF: 148 · ON_BRANCH: 141 · rationale_for: 97 · inherits: 70 · uses: 69 · implements: 53 · imports_from: 19 · references: 15


## Input Scope
- Requested: auto
- Resolved: committed (source: default-auto)
- Included files: 643 · Candidates: 925
- Excluded: 102 untracked · 1635 ignored · 30 sensitive · 2 missing committed
- Recommendation: Use --scope all or graphify.yaml inputs.corpus for a knowledge-base folder.

## Graph Freshness
- Built from Git commit: `e934800`
- Compare this hash to `git rev-parse HEAD` before trusting freshness-sensitive graph output.
## God Nodes (most connected - your core abstractions)
1. `CodeHygieneTest` - 43 edges
2. `FitnessAiService` - 37 edges
3. `Profile` - 28 edges
4. `UserDataLifecycleServiceIntegrationTest` - 24 edges
5. `TelegramBotService` - 23 edges
6. `User` - 22 edges
7. `JefitCsvParserAdapter` - 22 edges
8. `ObservationEvent` - 21 edges
9. `GlobalExceptionHandler` - 21 edges
10. `_make_project()` - 20 edges

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

### Community 1 - "Community 1"
Cohesion: 0.07
Nodes (54): _ensure_global_dirs(), _validate_file_path(), _validate_instinct_id(), _yaml_quote(), detect_project(), _update_registry(), load_registry(), parse_instinct_file() (+46 more)

### Community 21 - "Community 21"
Cohesion: 0.07
Nodes (1): Tests for continuous-learning-v2 instinct-cli.py  Covers:   - parse_instinct_

### Community 204 - "Community 204"
Cohesion: 1.00
Nodes (2): project_tree(), Create a realistic project directory tree for testing.

### Community 203 - "Community 203"
Cohesion: 1.00
Nodes (2): patch_globals(), Patch module-level globals to use tmp_path-based directories.

### Community 30 - "Community 30"
Cohesion: 0.10
Nodes (21): _make_project(), test_load_all_project_and_global(), test_load_all_project_overrides_global(), test_load_project_only_excludes_global(), test_load_all_empty(), test_cmd_status_no_instincts(), test_cmd_status_with_instincts(), test_cmd_status_returns_int() (+13 more)

### Community 205 - "Community 205"
Cohesion: 1.00
Nodes (2): test_parse_no_id_skipped(), Instincts without an 'id' field should be silently dropped.

### Community 206 - "Community 206"
Cohesion: 1.00
Nodes (2): test_validate_home_expansion(), Tilde expansion should work.

### Community 207 - "Community 207"
Cohesion: 1.00
Nodes (2): test_validate_relative_path(), Relative paths should be resolved.

### Community 208 - "Community 208"
Cohesion: 1.00
Nodes (2): test_detect_project_global_fallback(), When no git and no env var, should return global project.

### Community 209 - "Community 209"
Cohesion: 1.00
Nodes (2): test_detect_project_from_env(), CLAUDE_PROJECT_DIR env var should be used as project root.

### Community 210 - "Community 210"
Cohesion: 1.00
Nodes (2): test_detect_project_git_timeout(), Git timeout should fall through to global.

### Community 211 - "Community 211"
Cohesion: 1.00
Nodes (2): test_detect_project_creates_directories(), detect_project should create the project dir structure.

### Community 212 - "Community 212"
Cohesion: 1.00
Nodes (2): test_load_annotates_metadata(), Loaded instincts should have _source_file, _source_type, _scope_label.

### Community 213 - "Community 213"
Cohesion: 1.00
Nodes (2): test_load_defaults_scope_from_label(), If an instinct has no 'scope' in frontmatter, it should default to scope_label.

### Community 214 - "Community 214"
Cohesion: 1.00
Nodes (2): test_load_preserves_explicit_scope(), If frontmatter has explicit scope, it should be preserved.

### Community 215 - "Community 215"
Cohesion: 1.00
Nodes (2): test_load_handles_corrupt_file(), Corrupt YAML files should be warned about but not crash.

### Community 216 - "Community 216"
Cohesion: 1.00
Nodes (2): test_load_all_global_only(), Global project should only load global instincts.

### Community 217 - "Community 217"
Cohesion: 1.00
Nodes (2): test_load_project_only_global_fallback_loads_global(), Global fallback should return global instincts for project-only queries.

### Community 218 - "Community 218"
Cohesion: 1.00
Nodes (2): test_cmd_projects_empty_registry(), No projects should print helpful message.

### Community 219 - "Community 219"
Cohesion: 1.00
Nodes (2): test_promote_specific_not_found(), Promoting nonexistent instinct should fail.

### Community 220 - "Community 220"
Cohesion: 1.00
Nodes (2): test_promote_specific_rejects_invalid_id(), Path-like instinct IDs should be rejected before file writes.

### Community 221 - "Community 221"
Cohesion: 1.00
Nodes (2): test_promote_specific_already_global(), Promoting an instinct that already exists globally should fail.

### Community 222 - "Community 222"
Cohesion: 1.00
Nodes (2): test_promote_specific_success(), Promote a project instinct to global with --force.

### Community 223 - "Community 223"
Cohesion: 1.00
Nodes (2): test_promote_auto_no_candidates(), Auto-promote with no cross-project instincts should say so.

### Community 224 - "Community 224"
Cohesion: 1.00
Nodes (2): test_promote_auto_dry_run(), Dry run should list candidates but not write files.

### Community 225 - "Community 225"
Cohesion: 1.00
Nodes (2): test_promote_auto_writes_file(), Auto-promote with force should write global instinct file.

### Community 226 - "Community 226"
Cohesion: 1.00
Nodes (2): test_find_cross_project_single_project(), Single project should return nothing (need 2+).

### Community 46 - "Community 46"
Cohesion: 0.16
Nodes (8): CLI entry point for skill-comply., generate_spec(), Generate compliance specs from skill files using LLM., Generate a compliance spec from a skill/rule file.      Calls claude -p with t, extract_yaml(), Shared utilities for skill-comply scripts., Extract YAML from LLM output, stripping markdown fences if present., 2b1a12a chore: baseline before security hardening

### Community 29 - "Community 29"
Cohesion: 0.23
Nodes (19): classify_events(), _parse_classification(), Classify tool calls against compliance steps using LLM., Classify which tool calls match which compliance steps.      Returns {step_id:, Parse LLM classification output into {step_id: [event_indices]}., StepResult, ComplianceResult, _check_temporal_order() (+11 more)

### Community 101 - "Community 101"
Cohesion: 0.33
Nodes (6): Detector, parse_trace(), parse_spec(), Parse observation traces (JSONL) and compliance specs (YAML)., Parse a JSONL observation trace file into sorted events., Parse a YAML compliance spec file.

### Community 149 - "Community 149"
Cohesion: 0.80
Nodes (4): generate_report(), _overall_compliance(), _step_compliance_rate(), _steps_to_promote()

### Community 41 - "Community 41"
Cohesion: 0.22
Nodes (14): ScenarioRun, run_scenario(), _safe_sandbox_dir(), _setup_sandbox(), _parse_stream_json(), Run scenarios via claude -p and parse tool calls from stream-json output., Execute a scenario and extract tool calls from stream-json output., Sanitize scenario ID and ensure path stays within sandbox base. (+6 more)

### Community 52 - "Community 52"
Cohesion: 0.15
Nodes (5): _mock_compliant_classification(), _mock_noncompliant_classification(), TestGradeEdgeCases, Simulate LLM correctly classifying a compliant trace., Simulate LLM classifying a noncompliant trace (impl before test).

### Community 105 - "Community 105"
Cohesion: 0.29
Nodes (1): TestGradeCompliant

### Community 104 - "Community 104"
Cohesion: 0.29
Nodes (2): TestGradeNoncompliant, write_test has before_step=write_impl, but test is written AFTER impl.

### Community 106 - "Community 106"
Cohesion: 0.29
Nodes (1): TestParseTrace

### Community 122 - "Community 122"
Cohesion: 0.33
Nodes (1): TestParseSpec

### Community 32 - "Community 32"
Cohesion: 0.15
Nodes (19): default_output_dir(), ensure_private_dir(), parse_args(), log(), append_event(), write_pid(), cleanup_pid(), is_fatal_error() (+11 more)

### Community 7 - "Community 7"
Cohesion: 0.09
Nodes (22): FitnessAppApplication, AiInsightEntity, executionOrDefaults(), defaults(), RateLimitInterceptor, HandlerInterceptor, WebMvcConfig, WebMvcConfigurer (+14 more)

### Community 125 - "Community 125"
Cohesion: 0.50
Nodes (1): AiBudgetService

### Community 179 - "Community 179"
Cohesion: 0.67
Nodes (1): AiClientConfig

### Community 126 - "Community 126"
Cohesion: 0.70
Nodes (1): AiController

### Community 11 - "Community 11"
Cohesion: 0.07
Nodes (8): AiDurableJobExecutor, DurableJobExecutor, DurableJobController, DurableJobService, DurableJobUseCase, DurableJobWorker, NutritionSyncJobExecutor, DurableJobWorkerTest

### Community 92 - "Community 92"
Cohesion: 0.33
Nodes (2): AiExecutionGuard, AutoCloseable

### Community 22 - "Community 22"
Cohesion: 0.10
Nodes (9): AiTodayInsightResponse, UserTimeController, CurrentUserApi, WorkoutAnalyticController, WorkoutImportController, WorkoutQueryUseCase, 324b630 docs: add local runbook, 746f016 fix: add workout analytic path alias (+1 more)

### Community 68 - "Community 68"
Cohesion: 0.29
Nodes (3): AiInsightRepository, AiInsightPort, InsightSourceApi

### Community 10 - "Community 10"
Cohesion: 0.08
Nodes (19): JpaRepository, Long, ProfileJpaRepositoryImpl, ConversationStateRepository, ConversationStateEntity, TelegramUserRepository, WorkoutCardioJpaRepository, WorkoutCardioJpaEntity (+11 more)

### Community 38 - "Community 38"
Cohesion: 0.18
Nodes (7): AiInsightEntity, MemoryQueryUseCase, NutritionQueryUseCase, WorkoutDailyApi, WorkoutDailyApi, 20eb08b feat: include workout context in daily insights, 2d879ec refactor: split ai daily and telegram workflows

### Community 128 - "Community 128"
Cohesion: 0.50
Nodes (1): AiPromptRenderer

### Community 8 - "Community 8"
Cohesion: 0.06
Nodes (15): AppTimeConfig, ErrorCode, ai_usage_budget, Clock, WorkoutQueryUseCaseTest, TelegramNotificationListenerTest, 028d224 config: configure global UTC clock bean and align dev runtime properties, 1f2d197 Stabilize workout JDBC query tests (+7 more)

### Community 12 - "Community 12"
Cohesion: 0.17
Nodes (1): FitnessAiService

### Community 23 - "Community 23"
Cohesion: 0.17
Nodes (9): AiModelPort, user_memory, 03e0965 cleaned up, 2ffe3ae chore: normalize code comments, 31ee683 temp clean, 5896320 feat : added detailed recent insights, 7a1c705 Merge pull request #25 from DmitriyGrachev/feature/analysis-and-fixes, b47a94f feat : shart/long time memory impl (+1 more)

### Community 180 - "Community 180"
Cohesion: 0.67
Nodes (1): MoeOrchestrator

### Community 3 - "Community 3"
Cohesion: 0.07
Nodes (22): RateLimiterService, AiRateLimitApi, TelegramNoteListener, UserTimeApi, User, TelegramNotificationListener, ConversationStateUseCase, TelegramProperties (+14 more)

### Community 95 - "Community 95"
Cohesion: 0.48
Nodes (1): SmartAiRouter

### Community 115 - "Community 115"
Cohesion: 0.53
Nodes (1): AiProviderExceptionClassifier

### Community 13 - "Community 13"
Cohesion: 0.07
Nodes (11): AiModelPort, AiRateLimitException, AiUnavailableException, AiTimeoutException, TelegramWeightListener, TelegramAiResponseListener, TelegramBotConfig, UserAuthorityTest (+3 more)

### Community 171 - "Community 171"
Cohesion: 0.67
Nodes (1): GeminiAdapter

### Community 116 - "Community 116"
Cohesion: 0.60
Nodes (1): OpenRouterAdapter

### Community 145 - "Community 145"
Cohesion: 0.40
Nodes (1): AiInsightPort

### Community 174 - "Community 174"
Cohesion: 0.67
Nodes (1): AiContextService

### Community 150 - "Community 150"
Cohesion: 0.40
Nodes (1): AiInsightPersistenceService

### Community 59 - "Community 59"
Cohesion: 0.18
Nodes (2): telegram_delivery_outbox, 084756d feat: add durable job tracking, telegram link/outbox, gdpr lifecycle, ai safety, and time policy

### Community 56 - "Community 56"
Cohesion: 0.24
Nodes (1): AiSafetyService

### Community 4 - "Community 4"
Cohesion: 0.06
Nodes (12): WorkoutImportedEvent(), datesBetween(), WorkoutParserPort, WorkoutPersistenceAdapter, WorkoutPersistencePort, WorkoutImportService, ImportWorkoutUseCase, NutritionPersistenceAdapterPostgresIntegrationTest (+4 more)

### Community 102 - "Community 102"
Cohesion: 0.52
Nodes (1): DailyInsightService

### Community 175 - "Community 175"
Cohesion: 0.83
Nodes (1): DailyInsightSnapshotService

### Community 36 - "Community 36"
Cohesion: 0.20
Nodes (6): UserNoteUseCase, ProfileUseCase, WeightHistoryUseCase, TelegramNoteListenerTest, UserNoteControllerValidationTest, 426f943 Fix nutrition workout AI insight workflows

### Community 71 - "Community 71"
Cohesion: 0.38
Nodes (1): MonthlyReportService

### Community 66 - "Community 66"
Cohesion: 0.33
Nodes (1): ReportSnapshotHasher

### Community 176 - "Community 176"
Cohesion: 0.67
Nodes (1): TelegramAskAiService

### Community 73 - "Community 73"
Cohesion: 0.38
Nodes (1): WeeklyReportService

### Community 17 - "Community 17"
Cohesion: 0.07
Nodes (13): AiAuthException, ExternalApiException, AiBudgetExceededException, RuntimeException, AiBulkheadFullException, AiInvalidRequestException, AiUnavailableException, ExternalServiceUnavailableException (+5 more)

### Community 5 - "Community 5"
Cohesion: 0.07
Nodes (18): MonthlyReportController, UserApi, NutritionMonthlyApi, NutritionWeeklyApi, NutritionMonthlyApi, WorkoutMonthlyApi, WorkoutWeeklyApi, WorkoutMonthlyApi (+10 more)

### Community 182 - "Community 182"
Cohesion: 0.67
Nodes (1): MonthlyReportOrchestrator

### Community 183 - "Community 183"
Cohesion: 1.00
Nodes (1): MonthlyReportTransactionService

### Community 184 - "Community 184"
Cohesion: 0.67
Nodes (1): WeeklyReportOrchestrator

### Community 185 - "Community 185"
Cohesion: 1.00
Nodes (1): WeeklyReportTransactionService

### Community 20 - "Community 20"
Cohesion: 0.08
Nodes (14): WeeklyReportController, UserApi, NutritionWeeklyApi, WorkoutWeeklyApi, WorkoutExerciseJpaEntity, WorkoutSetJpaEntity, WorkoutSummaryDto, 3bdb197 docs: plan ingestion idempotency work (+6 more)

### Community 181 - "Community 181"
Cohesion: 0.67
Nodes (1): AiRateLimitApi

### Community 37 - "Community 37"
Cohesion: 0.13
Nodes (4): WorkoutCardioJpaEntity, workout_cardio, AiPromptRendererTest, 418dbcd Stabilize AI workout insight workflow

### Community 0 - "Community 0"
Cohesion: 0.05
Nodes (52): MemoryConfig, OpenApiEndpoint, AbstractPostgresIntegrationTest, 08c2c56 fix: expose secured actuator endpoints, 0d11de1 docs: design P0 data foundation phase, 12e4208 fix: update spring boot tomcat line, 12f46f9 conf : gitignore file, 1401f78 chore: add Codex project guidance (+44 more)

### Community 44 - "Community 44"
Cohesion: 0.21
Nodes (5): NutritionCommandPort, 64dd819 fix: reduce nutrition sync churn, e0ccccb fix: mark nutrition scheduler constructor for injection, eab64da fix: track nutrition sync freshness hashes, fa52037 fix: restore daily nutrition sync scheduler

### Community 18 - "Community 18"
Cohesion: 0.11
Nodes (12): LoginUseCase, RegisterUserPort, UserPersistencePort, WebConfig, WorkoutJpaEntity, WorkoutSummaryWeeklyDto, 3b09783 rafactoring: Created seperate auth modul for Security and User, 42a3900 Merge pull request #17 from DmitriyGrachev/PROD_refactor-auth-module (+4 more)

### Community 159 - "Community 159"
Cohesion: 0.50
Nodes (1): CurrentUserApi

### Community 132 - "Community 132"
Cohesion: 0.40
Nodes (1): UserTimeApi

### Community 201 - "Community 201"
Cohesion: 0.67
Nodes (1): AuthController

### Community 6 - "Community 6"
Cohesion: 0.08
Nodes (7): UserDataLifecycleController, UserDataLifecycleService, UserDataLifecycleUseCase, UserDataLifecycleServiceIntegrationTest, RollbackTestConfiguration, RollbackProbeParticipant, UserDataLifecycleParticipant

### Community 26 - "Community 26"
Cohesion: 0.11
Nodes (7): UserNoteController, UserNoteService, user_notes, MonthlyReportOrchestratorTest, 0219941 Scope user notes to authenticated users, 7deb6c9 Fix auth boundaries and monthly report period, 89603cd feat : add user notes

### Community 33 - "Community 33"
Cohesion: 0.13
Nodes (10): UserAuthenticationAdapter, UserAuthenticationPort, userdetails, LoginService, LoginUseCase, UserDetailsService, org, springframework (+2 more)

### Community 86 - "Community 86"
Cohesion: 0.29
Nodes (2): UserNoteJpaRepository, UserNote

### Community 64 - "Community 64"
Cohesion: 0.22
Nodes (3): UserNotePersistenceAdapter, UserNotePersistencePort, UserNoteServiceTest

### Community 65 - "Community 65"
Cohesion: 0.20
Nodes (5): UserPersistenceAdapter, UserPersistencePort, RegisterService, RegisterUserPort, RegisterServiceTest

### Community 118 - "Community 118"
Cohesion: 0.53
Nodes (1): UserTimeApiAdapter

### Community 39 - "Community 39"
Cohesion: 0.13
Nodes (1): UserNote

### Community 31 - "Community 31"
Cohesion: 0.10
Nodes (1): User

### Community 78 - "Community 78"
Cohesion: 0.22
Nodes (1): UserRepository

### Community 140 - "Community 140"
Cohesion: 0.40
Nodes (1): UserDataLifecycleUseCase

### Community 141 - "Community 141"
Cohesion: 0.40
Nodes (1): UserNoteUseCase

### Community 146 - "Community 146"
Cohesion: 0.40
Nodes (1): UserAuthenticationPort

### Community 147 - "Community 147"
Cohesion: 0.40
Nodes (1): UserNotePersistencePort

### Community 152 - "Community 152"
Cohesion: 0.60
Nodes (1): CurrentUserService

### Community 121 - "Community 121"
Cohesion: 0.47
Nodes (1): UserTimeService

### Community 109 - "Community 109"
Cohesion: 0.47
Nodes (1): SecurityConfig

### Community 85 - "Community 85"
Cohesion: 0.39
Nodes (2): AuthRateLimitFilter, OncePerRequestFilter

### Community 123 - "Community 123"
Cohesion: 0.53
Nodes (1): JwtCore

### Community 135 - "Community 135"
Cohesion: 0.50
Nodes (1): ApiErrorResponseWriter

### Community 27 - "Community 27"
Cohesion: 0.18
Nodes (1): GlobalExceptionHandler

### Community 188 - "Community 188"
Cohesion: 0.67
Nodes (1): TransactionalEventPublisher

### Community 170 - "Community 170"
Cohesion: 0.67
Nodes (1): StableBaseMetrics

### Community 166 - "Community 166"
Cohesion: 0.50
Nodes (1): DurableJobExecutor

### Community 54 - "Community 54"
Cohesion: 0.17
Nodes (1): DurableJobUseCase

### Community 99 - "Community 99"
Cohesion: 0.33
Nodes (3): JdbcMemoryCleanupAdapter, MemoryCleanupPort, FakeMemoryCleanupPort

### Community 165 - "Community 165"
Cohesion: 0.50
Nodes (1): MemoryQueryUseCase

### Community 196 - "Community 196"
Cohesion: 0.67
Nodes (1): MemoryCleanupPort

### Community 83 - "Community 83"
Cohesion: 0.25
Nodes (3): MemoryCleanupScheduler, MemoryCleanupServiceTest, 641c78f fix: clean up expired user memory

### Community 198 - "Community 198"
Cohesion: 0.67
Nodes (1): MemoryCleanupService

### Community 70 - "Community 70"
Cohesion: 0.42
Nodes (1): MemoryEventListener

### Community 120 - "Community 120"
Cohesion: 0.33
Nodes (1): MemoryService

### Community 48 - "Community 48"
Cohesion: 0.14
Nodes (1): DailyMacrosDto

### Community 15 - "Community 15"
Cohesion: 0.08
Nodes (9): NutritionController, NutritionService, ConnectFatSecretUseCase, SyncNutritionUseCase, NutritionControllerSyncTest, NutritionControllerValidationTest, NutritionSyncJobExecutorTest, 0543b9f fix: return sync completion status (+1 more)

### Community 24 - "Community 24"
Cohesion: 0.15
Nodes (8): Profile, WeightHistory, ProfileUseCase, weight_history, users, 5e01166 Merge pull request #24 from DmitriyGrachev/feature/user-context-enhancement, fa3fa65 feat : added user_note, weight, profile(empty), UserPort

### Community 202 - "Community 202"
Cohesion: 0.67
Nodes (1): TestController

### Community 117 - "Community 117"
Cohesion: 0.40
Nodes (1): NutritionJdbcQueryAdapter

### Community 40 - "Community 40"
Cohesion: 0.21
Nodes (1): NutritionPersistenceAdapter

### Community 173 - "Community 173"
Cohesion: 0.67
Nodes (1): ProfileJpaRepository

### Community 148 - "Community 148"
Cohesion: 0.40
Nodes (1): WeightHistoryJpaRepository

### Community 119 - "Community 119"
Cohesion: 0.40
Nodes (1): WeightHistoryRepository

### Community 19 - "Community 19"
Cohesion: 0.07
Nodes (1): Profile

### Community 47 - "Community 47"
Cohesion: 0.14
Nodes (1): WeightHistory

### Community 16 - "Community 16"
Cohesion: 0.07
Nodes (9): NutritionQueryUseCase, SyncNutritionUseCase, CaffeineCacheConfig, TempWorkout, TempExercise, ImportWorkoutUseCase, WorkoutPersistencePort, 12c65d2 made indopot (+1 more)

### Community 142 - "Community 142"
Cohesion: 0.40
Nodes (1): WeightHistoryUseCase

### Community 100 - "Community 100"
Cohesion: 0.29
Nodes (1): NutritionCommandPort

### Community 87 - "Community 87"
Cohesion: 0.39
Nodes (1): NutritionSyncScheduler

### Community 178 - "Community 178"
Cohesion: 0.50
Nodes (1): TimeEntryUtil

### Community 75 - "Community 75"
Cohesion: 0.25
Nodes (2): NutritionDay(), safeEntries()

### Community 139 - "Community 139"
Cohesion: 0.40
Nodes (2): TelegramUpdateHandler, TelegramLongPollingBot

### Community 154 - "Community 154"
Cohesion: 0.40
Nodes (1): TelegramLinkController

### Community 114 - "Community 114"
Cohesion: 0.33
Nodes (1): ConversationStateUseCase

### Community 151 - "Community 151"
Cohesion: 0.50
Nodes (1): ConversationStateService

### Community 25 - "Community 25"
Cohesion: 0.18
Nodes (1): TelegramBotService

### Community 72 - "Community 72"
Cohesion: 0.27
Nodes (1): TelegramLinkCodeManager

### Community 81 - "Community 81"
Cohesion: 0.22
Nodes (1): TelegramMessages

### Community 199 - "Community 199"
Cohesion: 0.67
Nodes (1): TelegramOutboxWorker

### Community 14 - "Community 14"
Cohesion: 0.06
Nodes (8): AskCommandHandler, CommandHandler, LinkCommandHandler, NoteCommandHandler, StartCommandHandler, TestGenerateHandler, TodayCommandHandler, WeightCommandHandler

### Community 136 - "Community 136"
Cohesion: 0.40
Nodes (1): CommandHandler

### Community 163 - "Community 163"
Cohesion: 0.50
Nodes (1): TelegramCommandParser

### Community 89 - "Community 89"
Cohesion: 0.25
Nodes (1): WorkoutDailyStatsDto

### Community 90 - "Community 90"
Cohesion: 0.25
Nodes (1): WorkoutWeeklyStatsDto

### Community 69 - "Community 69"
Cohesion: 0.33
Nodes (1): WorkoutJdbcQueryAdapter

### Community 28 - "Community 28"
Cohesion: 0.29
Nodes (1): JefitCsvParserAdapter

### Community 143 - "Community 143"
Cohesion: 0.40
Nodes (1): WorkoutQueryUseCase

### Community 172 - "Community 172"
Cohesion: 0.50
Nodes (1): WorkoutParserPort

### Community 189 - "Community 189"
Cohesion: 1.00
Nodes (2): user_notes, users

### Community 167 - "Community 167"
Cohesion: 1.00
Nodes (3): users, workout, workout_cardio

### Community 190 - "Community 190"
Cohesion: 1.00
Nodes (2): telegram_link_codes, users

### Community 191 - "Community 191"
Cohesion: 1.00
Nodes (2): durable_jobs, users

### Community 62 - "Community 62"
Cohesion: 0.29
Nodes (10): users, user_roles, fatsecret_day, fatsecret_food, fatsecret_connection, profile, workout, workout_exercises (+2 more)

### Community 192 - "Community 192"
Cohesion: 1.00
Nodes (2): ai_insights, users

### Community 144 - "Community 144"
Cohesion: 0.50
Nodes (4): telegram_users, users, conversation_state, conversation_history

### Community 9 - "Community 9"
Cohesion: 0.07
Nodes (1): CodeHygieneTest

### Community 2 - "Community 2"
Cohesion: 0.06
Nodes (13): AiBudgetServiceIntegrationTest, AbstractPostgresIntegrationTest, AiExternalIoTransactionBoundaryIntegrationTest, AbsoluteTimestampPostgresIntegrationTest, DomainInvariantPostgresIntegrationTest, UserAndOwnershipSchemaIntegrationTest, UserNoteForeignKeyIntegrationTest, DurableJobServiceIntegrationTest (+5 more)

### Community 91 - "Community 91"
Cohesion: 0.52
Nodes (1): AiControllerTest

### Community 127 - "Community 127"
Cohesion: 0.60
Nodes (1): AiDurableJobExecutorTest

### Community 93 - "Community 93"
Cohesion: 0.48
Nodes (1): AiExecutionGuardTest

### Community 43 - "Community 43"
Cohesion: 0.13
Nodes (1): FitnessAiServiceDelegationTest

### Community 53 - "Community 53"
Cohesion: 0.47
Nodes (1): FitnessAiServiceReportFreshnessTest

### Community 156 - "Community 156"
Cohesion: 0.50
Nodes (1): GeminiAdapterTest

### Community 45 - "Community 45"
Cohesion: 0.21
Nodes (5): MoeOrchestratorTest, DailyInsight, QuickAnalysis, WeeklyReport, MonthlyReport

### Community 94 - "Community 94"
Cohesion: 0.43
Nodes (1): OpenRouterAdapterTest

### Community 157 - "Community 157"
Cohesion: 0.50
Nodes (1): RateLimitInterceptorTest

### Community 129 - "Community 129"
Cohesion: 0.40
Nodes (1): RateLimiterServiceTest

### Community 34 - "Community 34"
Cohesion: 0.16
Nodes (2): SmartAiRouterTest, MutableClock

### Community 57 - "Community 57"
Cohesion: 0.26
Nodes (1): AiSafetyServiceTest

### Community 42 - "Community 42"
Cohesion: 0.23
Nodes (1): DailyInsightServiceTest

### Community 58 - "Community 58"
Cohesion: 0.32
Nodes (1): DailyInsightSnapshotServiceTest

### Community 80 - "Community 80"
Cohesion: 0.39
Nodes (1): TelegramAskAiServiceTest

### Community 158 - "Community 158"
Cohesion: 0.67
Nodes (1): ActuatorSecurityWebTest

### Community 186 - "Community 186"
Cohesion: 0.67
Nodes (1): ActuatorEndpoints

### Community 60 - "Community 60"
Cohesion: 0.18
Nodes (1): AuthControllerTest

### Community 108 - "Community 108"
Cohesion: 0.33
Nodes (2): AuthRateLimitWebTest, AuthEndpoints

### Community 160 - "Community 160"
Cohesion: 0.83
Nodes (1): OpenApiSecurityWebTest

### Community 96 - "Community 96"
Cohesion: 0.43
Nodes (1): SecurityConfigWebTest

### Community 130 - "Community 130"
Cohesion: 0.40
Nodes (1): TestEndpoints

### Community 74 - "Community 74"
Cohesion: 0.53
Nodes (1): SecurityProtectedEndpointsWebTest

### Community 131 - "Community 131"
Cohesion: 0.40
Nodes (1): UserNoteControllerTest

### Community 161 - "Community 161"
Cohesion: 0.50
Nodes (1): UserTimeControllerTest

### Community 197 - "Community 197"
Cohesion: 0.67
Nodes (1): UserPersistenceAdapterTest

### Community 88 - "Community 88"
Cohesion: 0.25
Nodes (1): UserDataLifecycleServiceTest

### Community 200 - "Community 200"
Cohesion: 0.67
Nodes (1): UserTimeApiServiceTest

### Community 177 - "Community 177"
Cohesion: 0.50
Nodes (1): UserTimeServiceTest

### Community 133 - "Community 133"
Cohesion: 0.40
Nodes (1): DateRangeTest

### Community 111 - "Community 111"
Cohesion: 0.33
Nodes (1): ApiErrorHandlerWebTest

### Community 112 - "Community 112"
Cohesion: 0.33
Nodes (1): ErrorEndpoints

### Community 195 - "Community 195"
Cohesion: 0.67
Nodes (1): StableBaseMetricsTest

### Community 124 - "Community 124"
Cohesion: 0.33
Nodes (1): WorkoutJdbcQueryAdapterSqlTest

### Community 103 - "Community 103"
Cohesion: 0.29
Nodes (1): DurableJobServiceTest

### Community 55 - "Community 55"
Cohesion: 0.17
Nodes (1): MemoryEventListenerTest

### Community 51 - "Community 51"
Cohesion: 0.31
Nodes (1): MemoryPgVectorIntegrationTest

### Community 61 - "Community 61"
Cohesion: 0.36
Nodes (1): MemoryServiceTest

### Community 168 - "Community 168"
Cohesion: 0.50
Nodes (1): ModuleArchitectureTest

### Community 76 - "Community 76"
Cohesion: 0.39
Nodes (1): NutritionMonthlyApiTest

### Community 77 - "Community 77"
Cohesion: 0.36
Nodes (1): NutritionPersistenceAdapterIdempotencyTest

### Community 193 - "Community 193"
Cohesion: 0.67
Nodes (1): NutritionServicePrivacyLoggingTest

### Community 63 - "Community 63"
Cohesion: 0.29
Nodes (1): NutritionServiceSyncWorkflowTest

### Community 169 - "Community 169"
Cohesion: 0.50
Nodes (1): TelegramWeightListenerPrivacyLoggingTest

### Community 194 - "Community 194"
Cohesion: 0.67
Nodes (1): WeightHistoryRepositoryTest

### Community 79 - "Community 79"
Cohesion: 0.22
Nodes (1): NutritionSyncSchedulerTest

### Community 187 - "Community 187"
Cohesion: 0.67
Nodes (1): NutritionDayStatusTest

### Community 82 - "Community 82"
Cohesion: 0.44
Nodes (1): StableBaseWorkflowIntegrationTest

### Community 98 - "Community 98"
Cohesion: 0.43
Nodes (1): TelegramUpdateHandlerPrivacyLoggingTest

### Community 153 - "Community 153"
Cohesion: 0.40
Nodes (1): TelegramLinkControllerTest

### Community 67 - "Community 67"
Cohesion: 0.22
Nodes (1): TelegramBotServiceTest

### Community 113 - "Community 113"
Cohesion: 0.73
Nodes (1): AskCommandHandlerTest

### Community 97 - "Community 97"
Cohesion: 0.43
Nodes (1): LinkCommandHandlerTest

### Community 84 - "Community 84"
Cohesion: 0.46
Nodes (1): NoteCommandHandlerTest

### Community 137 - "Community 137"
Cohesion: 0.50
Nodes (1): StartCommandHandlerTest

### Community 164 - "Community 164"
Cohesion: 0.50
Nodes (1): TelegramCommandParserTest

### Community 138 - "Community 138"
Cohesion: 0.60
Nodes (1): TodayCommandHandlerTest

### Community 50 - "Community 50"
Cohesion: 0.29
Nodes (1): WeightCommandHandlerTest

### Community 107 - "Community 107"
Cohesion: 0.48
Nodes (1): JefitCsvParserAdapterTest

### Community 155 - "Community 155"
Cohesion: 0.80
Nodes (1): WorkoutAnalyticControllerTest

### Community 49 - "Community 49"
Cohesion: 0.30
Nodes (1): WorkoutPersistenceAdapterTest

### Community 35 - "Community 35"
Cohesion: 0.21
Nodes (1): WorkoutPersistenceAdapterPostgresIntegrationTest

## Knowledge Gaps
- **101 isolated node(s):** `Validate and resolve a file path, guarding against path traversal.      Raises`, `Validate instinct IDs before using them in filenames.`, `Quote a string for safe YAML frontmatter serialization.      Uses double quote`, `Detect current project context. Returns dict with id, name, root, project_dir.`, `Update the projects.json registry.      Uses file locking (where available) to` (+96 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 21`** (1 nodes): `Tests for continuous-learning-v2 instinct-cli.py  Covers:   - parse_instinct_`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 204`** (2 nodes): `project_tree()`, `Create a realistic project directory tree for testing.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 203`** (2 nodes): `patch_globals()`, `Patch module-level globals to use tmp_path-based directories.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 205`** (2 nodes): `test_parse_no_id_skipped()`, `Instincts without an 'id' field should be silently dropped.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 206`** (2 nodes): `test_validate_home_expansion()`, `Tilde expansion should work.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 207`** (2 nodes): `test_validate_relative_path()`, `Relative paths should be resolved.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 208`** (2 nodes): `test_detect_project_global_fallback()`, `When no git and no env var, should return global project.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 209`** (2 nodes): `test_detect_project_from_env()`, `CLAUDE_PROJECT_DIR env var should be used as project root.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 210`** (2 nodes): `test_detect_project_git_timeout()`, `Git timeout should fall through to global.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 211`** (2 nodes): `test_detect_project_creates_directories()`, `detect_project should create the project dir structure.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 212`** (2 nodes): `test_load_annotates_metadata()`, `Loaded instincts should have _source_file, _source_type, _scope_label.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 213`** (2 nodes): `test_load_defaults_scope_from_label()`, `If an instinct has no 'scope' in frontmatter, it should default to scope_label.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 214`** (2 nodes): `test_load_preserves_explicit_scope()`, `If frontmatter has explicit scope, it should be preserved.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 215`** (2 nodes): `test_load_handles_corrupt_file()`, `Corrupt YAML files should be warned about but not crash.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 216`** (2 nodes): `test_load_all_global_only()`, `Global project should only load global instincts.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 217`** (2 nodes): `test_load_project_only_global_fallback_loads_global()`, `Global fallback should return global instincts for project-only queries.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 218`** (2 nodes): `test_cmd_projects_empty_registry()`, `No projects should print helpful message.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 219`** (2 nodes): `test_promote_specific_not_found()`, `Promoting nonexistent instinct should fail.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 220`** (2 nodes): `test_promote_specific_rejects_invalid_id()`, `Path-like instinct IDs should be rejected before file writes.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 221`** (2 nodes): `test_promote_specific_already_global()`, `Promoting an instinct that already exists globally should fail.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 222`** (2 nodes): `test_promote_specific_success()`, `Promote a project instinct to global with --force.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 223`** (2 nodes): `test_promote_auto_no_candidates()`, `Auto-promote with no cross-project instincts should say so.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 224`** (2 nodes): `test_promote_auto_dry_run()`, `Dry run should list candidates but not write files.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 225`** (2 nodes): `test_promote_auto_writes_file()`, `Auto-promote with force should write global instinct file.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 226`** (2 nodes): `test_find_cross_project_single_project()`, `Single project should return nothing (need 2+).`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 105`** (1 nodes): `TestGradeCompliant`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 104`** (2 nodes): `TestGradeNoncompliant`, `write_test has before_step=write_impl, but test is written AFTER impl.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 106`** (1 nodes): `TestParseTrace`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 122`** (1 nodes): `TestParseSpec`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 125`** (1 nodes): `AiBudgetService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 179`** (1 nodes): `AiClientConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 126`** (1 nodes): `AiController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 92`** (2 nodes): `AiExecutionGuard`, `AutoCloseable`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 128`** (1 nodes): `AiPromptRenderer`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 12`** (1 nodes): `FitnessAiService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 180`** (1 nodes): `MoeOrchestrator`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 95`** (1 nodes): `SmartAiRouter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 115`** (1 nodes): `AiProviderExceptionClassifier`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 171`** (1 nodes): `GeminiAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 116`** (1 nodes): `OpenRouterAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 145`** (1 nodes): `AiInsightPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 174`** (1 nodes): `AiContextService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 150`** (1 nodes): `AiInsightPersistenceService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 59`** (2 nodes): `telegram_delivery_outbox`, `084756d feat: add durable job tracking, telegram link/outbox, gdpr lifecycle, ai safety, and time policy`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 56`** (1 nodes): `AiSafetyService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 102`** (1 nodes): `DailyInsightService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 175`** (1 nodes): `DailyInsightSnapshotService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 71`** (1 nodes): `MonthlyReportService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 66`** (1 nodes): `ReportSnapshotHasher`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 176`** (1 nodes): `TelegramAskAiService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 73`** (1 nodes): `WeeklyReportService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 182`** (1 nodes): `MonthlyReportOrchestrator`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 183`** (1 nodes): `MonthlyReportTransactionService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 184`** (1 nodes): `WeeklyReportOrchestrator`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 185`** (1 nodes): `WeeklyReportTransactionService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 181`** (1 nodes): `AiRateLimitApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 159`** (1 nodes): `CurrentUserApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 132`** (1 nodes): `UserTimeApi`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 201`** (1 nodes): `AuthController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 86`** (2 nodes): `UserNoteJpaRepository`, `UserNote`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 118`** (1 nodes): `UserTimeApiAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 39`** (1 nodes): `UserNote`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 31`** (1 nodes): `User`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 78`** (1 nodes): `UserRepository`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 140`** (1 nodes): `UserDataLifecycleUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 141`** (1 nodes): `UserNoteUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 146`** (1 nodes): `UserAuthenticationPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 147`** (1 nodes): `UserNotePersistencePort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 152`** (1 nodes): `CurrentUserService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 121`** (1 nodes): `UserTimeService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 109`** (1 nodes): `SecurityConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 85`** (2 nodes): `AuthRateLimitFilter`, `OncePerRequestFilter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 123`** (1 nodes): `JwtCore`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 135`** (1 nodes): `ApiErrorResponseWriter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 27`** (1 nodes): `GlobalExceptionHandler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 188`** (1 nodes): `TransactionalEventPublisher`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 170`** (1 nodes): `StableBaseMetrics`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 166`** (1 nodes): `DurableJobExecutor`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 54`** (1 nodes): `DurableJobUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 165`** (1 nodes): `MemoryQueryUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 196`** (1 nodes): `MemoryCleanupPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 198`** (1 nodes): `MemoryCleanupService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 70`** (1 nodes): `MemoryEventListener`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 120`** (1 nodes): `MemoryService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 48`** (1 nodes): `DailyMacrosDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 202`** (1 nodes): `TestController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 117`** (1 nodes): `NutritionJdbcQueryAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 40`** (1 nodes): `NutritionPersistenceAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 173`** (1 nodes): `ProfileJpaRepository`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 148`** (1 nodes): `WeightHistoryJpaRepository`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 119`** (1 nodes): `WeightHistoryRepository`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 19`** (1 nodes): `Profile`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 47`** (1 nodes): `WeightHistory`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 142`** (1 nodes): `WeightHistoryUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 100`** (1 nodes): `NutritionCommandPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 87`** (1 nodes): `NutritionSyncScheduler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 178`** (1 nodes): `TimeEntryUtil`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 75`** (2 nodes): `NutritionDay()`, `safeEntries()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 139`** (2 nodes): `TelegramUpdateHandler`, `TelegramLongPollingBot`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 154`** (1 nodes): `TelegramLinkController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 114`** (1 nodes): `ConversationStateUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 151`** (1 nodes): `ConversationStateService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 25`** (1 nodes): `TelegramBotService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 72`** (1 nodes): `TelegramLinkCodeManager`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 81`** (1 nodes): `TelegramMessages`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 199`** (1 nodes): `TelegramOutboxWorker`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 136`** (1 nodes): `CommandHandler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 163`** (1 nodes): `TelegramCommandParser`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 89`** (1 nodes): `WorkoutDailyStatsDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 90`** (1 nodes): `WorkoutWeeklyStatsDto`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 69`** (1 nodes): `WorkoutJdbcQueryAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 28`** (1 nodes): `JefitCsvParserAdapter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 143`** (1 nodes): `WorkoutQueryUseCase`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 172`** (1 nodes): `WorkoutParserPort`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 189`** (2 nodes): `user_notes`, `users`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 190`** (2 nodes): `telegram_link_codes`, `users`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 191`** (2 nodes): `durable_jobs`, `users`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 192`** (2 nodes): `ai_insights`, `users`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 9`** (1 nodes): `CodeHygieneTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 91`** (1 nodes): `AiControllerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 127`** (1 nodes): `AiDurableJobExecutorTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 93`** (1 nodes): `AiExecutionGuardTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 43`** (1 nodes): `FitnessAiServiceDelegationTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 53`** (1 nodes): `FitnessAiServiceReportFreshnessTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 156`** (1 nodes): `GeminiAdapterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 94`** (1 nodes): `OpenRouterAdapterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 157`** (1 nodes): `RateLimitInterceptorTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 129`** (1 nodes): `RateLimiterServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 34`** (2 nodes): `SmartAiRouterTest`, `MutableClock`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 57`** (1 nodes): `AiSafetyServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 42`** (1 nodes): `DailyInsightServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 58`** (1 nodes): `DailyInsightSnapshotServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 80`** (1 nodes): `TelegramAskAiServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 158`** (1 nodes): `ActuatorSecurityWebTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 186`** (1 nodes): `ActuatorEndpoints`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 60`** (1 nodes): `AuthControllerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 108`** (2 nodes): `AuthRateLimitWebTest`, `AuthEndpoints`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 160`** (1 nodes): `OpenApiSecurityWebTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 96`** (1 nodes): `SecurityConfigWebTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 130`** (1 nodes): `TestEndpoints`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 74`** (1 nodes): `SecurityProtectedEndpointsWebTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 131`** (1 nodes): `UserNoteControllerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 161`** (1 nodes): `UserTimeControllerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 197`** (1 nodes): `UserPersistenceAdapterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 88`** (1 nodes): `UserDataLifecycleServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 200`** (1 nodes): `UserTimeApiServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 177`** (1 nodes): `UserTimeServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 133`** (1 nodes): `DateRangeTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 111`** (1 nodes): `ApiErrorHandlerWebTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 112`** (1 nodes): `ErrorEndpoints`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 195`** (1 nodes): `StableBaseMetricsTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 124`** (1 nodes): `WorkoutJdbcQueryAdapterSqlTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 103`** (1 nodes): `DurableJobServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 55`** (1 nodes): `MemoryEventListenerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 51`** (1 nodes): `MemoryPgVectorIntegrationTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 61`** (1 nodes): `MemoryServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 168`** (1 nodes): `ModuleArchitectureTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 76`** (1 nodes): `NutritionMonthlyApiTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 77`** (1 nodes): `NutritionPersistenceAdapterIdempotencyTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 193`** (1 nodes): `NutritionServicePrivacyLoggingTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 63`** (1 nodes): `NutritionServiceSyncWorkflowTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 169`** (1 nodes): `TelegramWeightListenerPrivacyLoggingTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 194`** (1 nodes): `WeightHistoryRepositoryTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 79`** (1 nodes): `NutritionSyncSchedulerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 187`** (1 nodes): `NutritionDayStatusTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 82`** (1 nodes): `StableBaseWorkflowIntegrationTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 98`** (1 nodes): `TelegramUpdateHandlerPrivacyLoggingTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 153`** (1 nodes): `TelegramLinkControllerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 67`** (1 nodes): `TelegramBotServiceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 113`** (1 nodes): `AskCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 97`** (1 nodes): `LinkCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 84`** (1 nodes): `NoteCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 137`** (1 nodes): `StartCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 164`** (1 nodes): `TelegramCommandParserTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 138`** (1 nodes): `TodayCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 50`** (1 nodes): `WeightCommandHandlerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 107`** (1 nodes): `JefitCsvParserAdapterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 155`** (1 nodes): `WorkoutAnalyticControllerTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 49`** (1 nodes): `WorkoutPersistenceAdapterTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 35`** (1 nodes): `WorkoutPersistenceAdapterPostgresIntegrationTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CodeHygieneTest` connect `Community 9` to `Community 0`?**
  _High betweenness centrality (0.034) - this node is a cross-community bridge._
- **Why does `FitnessAiService` connect `Community 12` to `Community 7`?**
  _High betweenness centrality (0.029) - this node is a cross-community bridge._
- **Why does `Profile` connect `Community 19` to `Community 18`?**
  _High betweenness centrality (0.022) - this node is a cross-community bridge._
- **What connects `Validate and resolve a file path, guarding against path traversal.      Raises`, `Validate instinct IDs before using them in filenames.`, `Quote a string for safe YAML frontmatter serialization.      Uses double quote` to the rest of the system?**
  _101 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.07205387205387205 - nodes in this community are weakly interconnected._
- **Should `Community 21` be split into smaller, more focused modules?**
  _Cohesion score 0.07407407407407407 - nodes in this community are weakly interconnected._
- **Should `Community 30` be split into smaller, more focused modules?**
  _Cohesion score 0.09523809523809523 - nodes in this community are weakly interconnected._