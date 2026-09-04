package com.fit.fitnessapp;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static org.assertj.core.api.Assertions.assertThat;

class CodeHygieneTest {

    private static final List<String> MODULE_NAMES = List.of(
            "ai",
            "analytics",
            "api",
            "auth",
            "exception",
            "memory",
            "nutrition",
            "telegram",
            "workout"
    );

    private static final List<String> AI_ROOT_IMPLEMENTATION_IMPORTS = List.of(
            "com.fit.fitnessapp.ai.AiController",
            "com.fit.fitnessapp.ai.AiInsightEntity",
            "com.fit.fitnessapp.ai.AiInsightRepository",
            "com.fit.fitnessapp.ai.AiPromptRenderer",
            "com.fit.fitnessapp.ai.AiProperties",
            "com.fit.fitnessapp.ai.FitnessAiService",
            "com.fit.fitnessapp.ai.MoeOrchestrator",
            "com.fit.fitnessapp.ai.RateLimiterService"
    );

    @Test
    void productionCodeDoesNotWriteDirectlyToStdoutOrStderr() throws IOException {
        List<String> offenders;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            offenders = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::containsConsolePrint)
                    .map(Path::toString)
                    .toList();
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    void applicationLayerDoesNotImportAdaptersOrPersistenceEntities() throws IOException {
        List<String> offenders;
        try (var files = Files.walk(Path.of("src/main/java/com/fit/fitnessapp"))) {
            offenders = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("\\application\\")
                            || path.toString().contains("/application/"))
                    .filter(this::importsAdaptersOrPersistence)
                    .map(Path::toString)
                    .toList();
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    void modulesDoNotImportOtherModulesPersistenceAdapters() throws IOException {
        List<String> offenders;
        try (var files = Files.walk(Path.of("src/main/java/com/fit/fitnessapp"))) {
            offenders = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::importsOtherModulePersistenceAdapter)
                    .map(Path::toString)
                    .toList();
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    void modulesDoNotImportAiRootImplementationClasses() throws IOException {
        List<String> offenders;
        try (var files = Files.walk(Path.of("src/main/java/com/fit/fitnessapp"))) {
            offenders = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/ai/"))
                    .filter(this::importsAiRootImplementationClass)
                    .map(Path::toString)
                    .toList();
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    void mainApplicationPropertiesDoesNotForceDevProfileOrSqlLogging() throws IOException {
        Properties properties = loadProperties("src/main/resources/application.properties");

        assertThat(properties).doesNotContainKey("spring.profiles.active");
        assertThat(properties.getProperty("spring.jpa.show-sql")).isEqualTo("false");
    }

    @Test
    void mainApplicationPropertiesDocumentsRequiredEnvironmentVariables() throws IOException {
        Properties properties = loadProperties("src/main/resources/application.properties");

        assertThat(properties)
                .containsEntry("spring.datasource.url", "${DB_URL}")
                .containsEntry("spring.datasource.username", "${DB_USERNAME}")
                .containsEntry("spring.datasource.password", "${DB_PASSWORD}")
                .containsEntry("telegram.bot.token", "${TELEGRAM_BOT_TOKEN}")
                .containsEntry("fitness.app.secret", "${FITNESS_APP_SECRET}")
                .containsEntry("fatsecret.consumer-key", "${FATSECRET_CONSUMER_KEY}")
                .containsEntry("fatsecret.consumer-secret", "${FATSECRET_CONSUMER_SECRET}")
                .containsEntry("fatsecret.client-id", "${FATSECRET_CLIENT_ID}")
                .containsEntry("fatsecret.client-secret", "${FATSECRET_CLIENT_SECRET}")
                .containsEntry("fatsecret.redirect-uri", "${FATSECRET_REDIRECT_URI}")
                .containsEntry("fatsecret.callback-url", "${FATSECRET_CALLBACK_URL}")
                .containsEntry("fatsecret.token-encryption.key", "${FATSECRET_TOKEN_ENCRYPTION_KEY:}")
                .containsEntry("spring.ai.openai.api-key", "${OPENROUTER_API_KEY}")
                .containsEntry("spring.ai.google.genai.api-key", "${GEMINI_API_KEY}");
    }

    @Test
    void readmeDocumentsLocalRunbookAndSecretHygiene() throws IOException {
        String readme = Files.readString(Path.of("README.md"));

        assertThat(readme)
                .contains("docker compose up")
                .contains("mvn test")
                .contains("mvn test -Parchitecture")
                .contains("DB_URL")
                .contains("TELEGRAM_BOT_TOKEN")
                .contains("FATSECRET_TOKEN_ENCRYPTION_KEY")
                .contains("OPENROUTER_API_KEY")
                .contains("GEMINI_API_KEY")
                .contains(".env")
                .contains("must not be committed");
    }

    @Test
    void authAndAiControllersUseTypedResponseContracts() throws IOException {
        String aiController = Files.readString(Path.of(
                "src/main/java/com/fit/fitnessapp/ai/AiController.java"));
        String authController = Files.readString(Path.of(
                "src/main/java/com/fit/fitnessapp/auth/adapter/in/web/AuthController.java"));

        assertThat(aiController)
                .doesNotContain("ResponseEntity<?>")
                .doesNotContain("Map.of(");
        assertThat(authController)
                .doesNotContain("ResponseEntity<?>")
                .doesNotContain("Registered successfully!");
    }

    @Test
    void telegramLinkCodeHasSingleProductionService() throws IOException {
        List<String> offenders = new ArrayList<>();
        collectObsoleteTelegramLinkOffenders(Path.of("src/main/java"), offenders);
        collectObsoleteTelegramLinkOffenders(Path.of("src/test/java"), offenders);

        assertThat(offenders).isEmpty();
    }

    @Test
    void jefitParserDoesNotSilentlyIgnoreMalformedRows() throws IOException {
        String parser = Files.readString(Path.of(
                "src/main/java/com/fit/fitnessapp/workout/adapter/out/parser/JefitCsvParserAdapter.java"));

        assertThat(parser)
                .doesNotContain("catch (Exception ignored)")
                .contains("WorkoutImportWarning");
    }

    @Test
    void workoutManyToOneAssociationsAreLazy() throws IOException {
        String workoutExerciseEntity = Files.readString(Path.of(
                "src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/entity/WorkoutExerciseJpaEntity.java"));
        String workoutSetEntity = Files.readString(Path.of(
                "src/main/java/com/fit/fitnessapp/workout/adapter/out/persistence/entity/WorkoutSetJpaEntity.java"));

        assertThat(workoutExerciseEntity).contains("@ManyToOne(fetch = FetchType.LAZY)");
        assertThat(workoutSetEntity).contains("@ManyToOne(fetch = FetchType.LAZY)");
    }

    @Test
    void aiPromptsLiveInVersionedResources() throws IOException {
        String fitnessAiService = Files.readString(Path.of(
                "src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java"));
        List<Path> promptFiles = List.of(
                Path.of("src/main/resources/ai/prompts/daily-insight-v2.md"),
                Path.of("src/main/resources/ai/prompts/weekly-report-v2.md"),
                Path.of("src/main/resources/ai/prompts/monthly-report-v2.md"),
                Path.of("src/main/resources/ai/prompts/telegram-ask-v2.md")
        );

        assertThat(fitnessAiService)
                .doesNotContain("You are a professional fitness dietitian")
                .doesNotContain("Act as a professional fitness dietitian and trainer")
                .doesNotContain("Answer the user's question based on their data and history");
        for (Path promptFile : promptFiles) {
            assertThat(promptFile).exists();
            String prompt = Files.readString(promptFile);
            assertThat(prompt)
                    .contains("name:")
                    .contains("version: v2");
        }
    }

    @Test
    void fatSecretAdapterDoesNotLogOrExposeRawApiBodies() throws IOException {
        String adapter = Files.readString(Path.of(
                "src/main/java/com/fit/fitnessapp/nutrition/adapter/out/api/FatSecretApiAdapter.java"));

        assertThat(adapter)
                .doesNotContain("log.debug(\"FatSecret food entries response [{}]: {}\", response.getCode(), response.getBody())")
                .doesNotContain("log.debug(\"FatSecret monthly food entries response [{}]: {}\", response.getCode(), response.getBody())")
                .doesNotContain("log.debug(\"FatSecret getWeightHistory response [{}]: {}\", response.getCode(), response.getBody())")
                .doesNotContain("log.debug(\"FatSecret updateWeight response [{}]: {}\", response.getCode(), response.getBody())")
                .doesNotContain("\" + response.getBody()");
    }

    @Test
    void aiInsightResponseContractHasSingleCanonicalType() {
        assertThat(Path.of("src/main/java/com/fit/fitnessapp/api/NutritionInsightResponse.java"))
                .doesNotExist();
        assertThat(Path.of("src/main/java/com/fit/fitnessapp/ai/domain/response/NutritionInsightResponse.java"))
                .exists();
    }

    @Test
    void aiModuleDoesNotExposeCallerScopedMemoryToolBean() {
        assertThat(Path.of("src/main/java/com/fit/fitnessapp/ai/FitnessAiTools.java"))
                .doesNotExist();
    }

    @Test
    void defaultTestProfileDoesNotDependOnH2() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        String testProperties = Files.readString(Path.of("src/test/resources/application-test.properties"));

        assertThat(pom)
                .doesNotContain("com.h2database")
                .doesNotContain("<artifactId>h2</artifactId>");
        assertThat(testProperties)
                .doesNotContain("jdbc:h2")
                .doesNotContain("MODE=PostgreSQL")
                .doesNotContain("spring.flyway.enabled=false");
    }

    @Test
    void jwtExpirationIsExternalizedToApplicationProperties() throws IOException {
        String jwtCore = Files.readString(Path.of("src/main/java/com/fit/fitnessapp/auth/infrastructure/utils/JwtCore.java"));
        Properties properties = loadProperties("src/main/resources/application.properties");

        assertThat(jwtCore)
                .doesNotContain("86400000")
                .doesNotContain("Duration.ofHours(24)");
        assertThat(properties).containsKey("fitness.app.jwt-expiration");
    }

    @Test
    void springAiVersionUsesCveFixedLine() throws Exception {
        String springAiVersion = mavenProperty("spring-ai.version");

        assertThat(compareVersions(springAiVersion, "1.1.3"))
                .as("spring-ai.version must be >= 1.1.3 for CVE-2026-22729")
                .isGreaterThanOrEqualTo(0);
        assertThat(springAiVersion).isNotEqualTo("1.1.2");
    }

    @Test
    void springBootParentUsesTomcatCveFixedPatchLine() throws Exception {
        String springBootVersion = mavenParentVersion();

        assertThat(compareVersions(springBootVersion, "3.4.4"))
                .as("Spring Boot parent must be >= 3.4.4 so managed Tomcat is outside CVE-2025-24813")
                .isGreaterThanOrEqualTo(0);
    }

    @Test
    void jjwtDependenciesUseCurrentLine() throws Exception {
        List<String> jjwtVersions = mavenDependencyVersions("io.jsonwebtoken");

        assertThat(jjwtVersions).hasSize(3);
        assertThat(jjwtVersions).doesNotContain("0.11.5");
        assertThat(jjwtVersions)
                .allSatisfy(version -> assertThat(compareVersions(version, "0.13.0"))
                        .as("JJWT dependencies must use the current 0.13.x line")
                        .isGreaterThanOrEqualTo(0));
    }

    @Test
    void architectureHotspotsUsePublicModuleContracts() throws IOException {
        String fitnessAiService = Files.readString(Path.of(
                "src/main/java/com/fit/fitnessapp/ai/FitnessAiService.java"));
        String askCommandHandler = Files.readString(Path.of(
                "src/main/java/com/fit/fitnessapp/telegram/application/service/handlers/AskCommandHandler.java"));
        String globalExceptionHandler = Files.readString(Path.of(
                "src/main/java/com/fit/fitnessapp/exception/GlobalExceptionHandler.java"));

        assertThat(fitnessAiService)
                .doesNotContain("com.fit.fitnessapp.analytics.WeeklyReportRequestedEvent")
                .doesNotContain("com.fit.fitnessapp.analytics.MonthlyReportRequestedEvent")
                .doesNotContain("com.fit.fitnessapp.nutrition.NutritionSyncedEvent");
        assertThat(askCommandHandler)
                .doesNotContain("com.fit.fitnessapp.ai.RateLimiterService");
        assertThat(globalExceptionHandler)
                .doesNotContain("com.fit.fitnessapp.ai.exception")
                .doesNotContain("com.fit.fitnessapp.nutrition.domain.MissingFatSecretConnectionException");
    }

    @Test
    void productionCodeDoesNotContainMojibakeArtifacts() throws IOException {
        List<String> offenders;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            offenders = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")
                            || path.toString().endsWith(".properties"))
                    .filter(this::containsMojibake)
                    .map(Path::toString)
                    .toList();
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    void productionCodeDoesNotContainKnownUtf8MojibakeSequences() throws IOException {
        List<String> mojibakeArtifacts = List.of(
                "\u0420\u045f",
                "\u0420\u2019",
                "\u0420\u2014",
                "\u0432\u20ac",
                "\u0432\u2020",
                "\ufffd"
        );

        List<String> offenders;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            offenders = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")
                            || path.toString().endsWith(".properties"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return mojibakeArtifacts.stream().anyMatch(source::contains);
                        } catch (IOException e) {
                            throw new IllegalStateException("Could not read " + path, e);
                        }
                    })
                    .map(Path::toString)
                    .toList();
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    void userMemoryDoesNotCreateInvalidHnswIndexFor2048Dimensions() throws IOException {
        List<Path> memoryIndexMigrations;
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            memoryIndexMigrations = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .contains("recreate_user_memory_hnsw_index"))
                    .toList();
        }

        assertThat(memoryIndexMigrations).hasSize(1);
        String migrationSql = Files.readString(memoryIndexMigrations.getFirst()).toLowerCase();

        assertThat(migrationSql)
                .contains("vector(2048)")
                .contains("at most 2000 dimensions")
                .contains("drop index if exists idx_user_memory_embedding")
                .doesNotContain("using hnsw");
    }

    @Test
    void pgvectorConfigurationMatchesRuntimeUserMemoryTable() throws IOException {
        Properties properties = loadProperties("src/main/resources/application.properties");
        String memoryConfig = Files.readString(Path.of(
                "src/main/java/com/fit/fitnessapp/memory/infrastructure/config/MemoryConfig.java"));

        assertThat(properties)
                .containsEntry("spring.ai.vectorstore.pgvector.table-name", "user_memory")
                .containsEntry("spring.ai.vectorstore.pgvector.index-type", "NONE")
                .containsEntry("spring.ai.vectorstore.pgvector.dimension", "2048");
        assertThat(memoryConfig)
                .contains(".vectorTableName(\"user_memory\")")
                .contains(".dimensions(2048)")
                .doesNotContain("vector_store")
                .doesNotContain("pgvector max 2000");
    }

    @Test
    void springAiTwoConfigurationUsesTopLevelModelProperties() throws IOException {
        Properties production = loadProperties("src/main/resources/application.properties");
        Properties test = loadProperties("src/test/resources/application-test.properties");
        String postgresSupport = Files.readString(Path.of(
                "src/test/java/com/fit/fitnessapp/support/AbstractPostgresIntegrationTest.java"));

        assertThat(production)
                .containsEntry("spring.ai.openai.chat.temperature", "0.7")
                .containsEntry("spring.ai.openai.chat.max-tokens", "8192")
                .containsEntry("spring.ai.google.genai.chat.model", "gemini-2.5-flash")
                .containsEntry("spring.ai.google.genai.chat.temperature", "0.7")
                .containsEntry("spring.ai.google.genai.chat.max-output-tokens", "8192")
                .containsEntry("spring.ai.model.embedding.text", "none")
                .containsEntry(
                        "spring.ai.openai.embedding.model",
                        "nvidia/llama-nemotron-embed-vl-1b-v2:free")
                .doesNotContainKeys(
                        "spring.ai.openai.chat.options.temperature",
                        "spring.ai.openai.chat.options.max-tokens",
                        "spring.ai.google.genai.chat.options.model",
                        "spring.ai.google.genai.chat.options.temperature",
                        "spring.ai.google.genai.chat.options.max-output-tokens",
                        "spring.ai.google.genai.embedding.enabled",
                        "spring.ai.google.genai.embedding.options.model",
                        "spring.ai.openai.embedding.options.model");
        assertThat(test)
                .containsEntry("spring.ai.model.embedding.text", "none")
                .containsEntry("spring.ai.openai.embedding.model", "test-embedding-model")
                .doesNotContainKeys(
                        "spring.ai.google.genai.embedding.enabled",
                        "spring.ai.openai.embedding.options.model");
        assertThat(postgresSupport)
                .contains("\"spring.ai.model.embedding.text\"")
                .contains("\"spring.ai.openai.embedding.model\"")
                .doesNotContain("spring.ai.google.genai.embedding.enabled")
                .doesNotContain("spring.ai.openai.embedding.options.model");
    }

    @Test
    void springFourBaselineDoesNotReintroduceLegacyDependencyBoundaries() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        List<String> legacyJacksonImports;
        try (var files = Files.walk(Path.of("src"))) {
            legacyJacksonImports = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::importsLegacyJacksonRuntime)
                    .map(Path::toString)
                    .toList();
        }

        assertThat(pom)
                .doesNotContain("<ignoredUsedUndeclaredDependencies>")
                .doesNotContain("<ignoredUsedUndeclaredDependency>")
                .doesNotContain("spring-ai-pgvector-store-spring-boot-starter");
        assertThat(legacyJacksonImports).isEmpty();
    }

    @Test
    void telegramIntegrationUsesBootFourCompatibleLibraryBoundary() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        String updateHandler = Files.readString(Path.of(
                "src/main/java/com/fit/fitnessapp/telegram/adapter/in/TelegramUpdateHandler.java"));
        String botService = Files.readString(Path.of(
                "src/main/java/com/fit/fitnessapp/telegram/application/service/TelegramBotService.java"));
        String botConfig = Files.readString(Path.of(
                "src/main/java/com/fit/fitnessapp/telegram/infrastructure/config/TelegramBotConfig.java"));

        assertThat(pom)
                .contains("<artifactId>telegrambots-longpolling</artifactId>")
                .contains("<artifactId>telegrambots-client</artifactId>")
                .contains("<artifactId>telegrambots-meta</artifactId>")
                .doesNotContain("<artifactId>telegrambots-spring-boot-starter</artifactId>");
        assertThat(updateHandler)
                .contains("LongPollingSingleThreadUpdateConsumer")
                .doesNotContain("TelegramLongPollingBot");
        assertThat(botService)
                .contains("TelegramClient")
                .doesNotContain("AbsSender");
        assertThat(botConfig)
                .contains("TelegramBotsLongPollingApplication")
                .contains("OkHttpTelegramClient")
                .doesNotContain("TelegramBotsApi")
                .doesNotContain("DefaultBotSession");
    }

    @Test
    void modulithTwoUsesFlywayOwnedCurrentPublicationSchema() throws IOException {
        Properties properties = loadProperties("src/main/resources/application.properties");
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V33__upgrade_event_publication_lifecycle.sql"))
                .toLowerCase();

        assertThat(properties)
                .containsEntry("spring.modulith.events.republish-outstanding-events-on-restart", "true")
                .containsEntry("spring.modulith.events.jdbc.schema-initialization.enabled", "false")
                .containsEntry("spring.modulith.events.jdbc.use-legacy-structure", "false")
                .doesNotContainKey("spring.modulith.republish-outstanding-events-on-restart");
        assertThat(migration)
                .contains("add column status text")
                .contains("add column completion_attempts int")
                .contains("add column last_resubmission_date timestamp with time zone")
                .contains("when completion_date is null then 'failed'")
                .contains("else 'completed'")
                .contains("event_publication_serialized_event_hash_idx");
    }

    @Test
    void devPgvectorConfigurationMatchesRuntimeUserMemoryTable() throws IOException {
        Properties properties = loadProperties("src/main/resources/application-dev.properties");
        String devProfile = Files.readString(Path.of("src/main/resources/application-dev.properties"));

        assertThat(properties)
                .containsEntry("spring.ai.vectorstore.pgvector.initialize-schema", "false")
                .containsEntry("spring.ai.vectorstore.pgvector.table-name", "user_memory")
                .containsEntry("spring.ai.vectorstore.pgvector.index-type", "NONE")
                .containsEntry("spring.ai.vectorstore.pgvector.dimension", "2048");
        assertThat(devProfile)
                .doesNotContain("vector_store")
                .doesNotContain("HNSW");
    }

    @Test
    void userNotesForeignKeyIsAddedByDedicatedMigration() throws IOException {
        List<Path> fkMigrations;
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            fkMigrations = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .contains("add_user_notes_user_fk"))
                    .toList();
        }

        assertThat(fkMigrations).hasSize(1);
        String migrationSql = Files.readString(fkMigrations.getFirst()).toLowerCase()
                .replaceAll("\\s+", " ");
        String originalUserNotesMigration = Files.readString(Path.of(
                "src/main/resources/db/migration/V3__create_user_notes_table.sql")).toLowerCase();

        assertThat(migrationSql)
                .contains("alter table user_notes")
                .contains("add constraint fk_user_notes_user")
                .contains("foreign key (user_id) references users (id)")
                .contains("on delete cascade");
        assertThat(originalUserNotesMigration).doesNotContain("fk_user_notes_user");
    }

    private boolean containsConsolePrint(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains("System.out.println") || source.contains("System.err.println");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    private boolean importsLegacyJacksonRuntime(Path path) {
        try {
            String source = Files.readString(path);
            String importPrefix = "import com.fasterxml.jackson.";
            return source.contains(importPrefix + "core.")
                    || source.contains(importPrefix + "databind.")
                    || source.contains(importPrefix + "datatype.")
                    || source.contains(importPrefix + "module.");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    private boolean importsAdaptersOrPersistence(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains(".adapter.")
                    || source.contains(".adapter\\.")
                    || source.contains(".adapter.out.persistence");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    private boolean importsOtherModulePersistenceAdapter(Path path) {
        try {
            String normalizedPath = path.toString().replace('\\', '/');
            String source = Files.readString(path);

            String currentModule = moduleNameFor(normalizedPath);
            if (currentModule == null) return false;

            return MODULE_NAMES.stream()
                    .filter(module -> !module.equals(currentModule))
                    .anyMatch(module -> source.contains("com.fit.fitnessapp." + module + ".adapter.out.persistence")
                            || source.contains("com.fit.fitnessapp." + module + ".infrastructure.persistence"));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    private boolean importsAiRootImplementationClass(Path path) {
        try {
            String source = Files.readString(path);
            return AI_ROOT_IMPLEMENTATION_IMPORTS.stream().anyMatch(source::contains);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    private String moduleNameFor(String normalizedPath) {
        String marker = "src/main/java/com/fit/fitnessapp/";
        int start = normalizedPath.indexOf(marker);
        if (start < 0) return null;
        String rest = normalizedPath.substring(start + marker.length());
        int separator = rest.indexOf('/');
        if (separator < 0) return null;
        String candidate = rest.substring(0, separator);
        if (!MODULE_NAMES.contains(candidate)) return null;
        return candidate;
    }

    private boolean containsMojibake(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains("Рђ")
                    || source.contains("Рџ")
                    || source.contains("Рњ")
                    || source.contains("рџ")
                    || source.contains("Ð")
                    || source.contains("Ñ")
                    || source.contains("в†")
                    || source.contains("вЂ")
                    || source.contains("пё")
                    || source.contains("�");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    private void collectObsoleteTelegramLinkOffenders(Path root, List<String> offenders) throws IOException {
        String obsoleteService = "Telegram" + "LinkService";
        String obsoleteVerifyMethod = "verify" + "Code(";
        String obsoleteLookupMethod = "getExisting" + "Code(";

        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains(obsoleteService)
                                    || source.contains(obsoleteVerifyMethod)
                                    || source.contains(obsoleteLookupMethod);
                        } catch (IOException e) {
                            throw new IllegalStateException("Could not read " + path, e);
                        }
                    })
                    .map(Path::toString)
                    .forEach(offenders::add);
        }
    }

    private Properties loadProperties(String path) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(path))) {
            properties.load(reader);
        }
        return properties;
    }

    private String mavenProperty(String propertyName)
            throws IOException, ParserConfigurationException, SAXException {
        Document document = pomDocument();
        var nodes = document.getElementsByTagName(propertyName);
        assertThat(nodes.getLength()).as(propertyName + " exists in pom.xml").isEqualTo(1);
        return nodes.item(0).getTextContent().trim();
    }

    private String mavenParentVersion()
            throws IOException, ParserConfigurationException, SAXException {
        Document document = pomDocument();
        Element parent = (Element) document.getElementsByTagName("parent").item(0);
        assertThat(parent).as("pom.xml has a parent").isNotNull();
        return parent.getElementsByTagName("version").item(0).getTextContent().trim();
    }

    private List<String> mavenDependencyVersions(String groupId)
            throws IOException, ParserConfigurationException, SAXException {
        Document document = pomDocument();
        var dependencies = document.getElementsByTagName("dependency");
        List<String> versions = new ArrayList<>();
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if (groupId.equals(childText(dependency, "groupId"))) {
                versions.add(childText(dependency, "version"));
            }
        }
        return versions;
    }

    private String childText(Element element, String tagName) {
        List<String> values = new ArrayList<>();
        for (var node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child && tagName.equals(child.getTagName())) {
                values.add(child.getTextContent().trim());
            }
        }

        assertThat(values).as(tagName + " exists").hasSize(1);
        return values.get(0);
    }

    private Document pomDocument() throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
    }

    private int compareVersions(String actual, String minimum) {
        String[] actualParts = actual.split("\\.");
        String[] minimumParts = minimum.split("\\.");
        int length = Math.max(actualParts.length, minimumParts.length);
        for (int i = 0; i < length; i++) {
            int actualPart = i < actualParts.length ? Integer.parseInt(actualParts[i]) : 0;
            int minimumPart = i < minimumParts.length ? Integer.parseInt(minimumParts[i]) : 0;
            if (actualPart != minimumPart) {
                return Integer.compare(actualPart, minimumPart);
            }
        }
        return 0;
    }
}
