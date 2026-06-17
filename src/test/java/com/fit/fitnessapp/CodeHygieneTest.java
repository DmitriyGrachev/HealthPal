package com.fit.fitnessapp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class CodeHygieneTest {

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
                .containsEntry("spring.ai.openai.api-key", "${OPENROUTER_API_KEY}")
                .containsEntry("spring.ai.google.genai.api-key", "${GEMINI_API_KEY}");
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

    private boolean containsConsolePrint(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains("System.out.println") || source.contains("System.err.println");
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

            if (normalizedPath.contains("/auth/")) {
                return source.contains("com.fit.fitnessapp.nutrition.adapter.out.persistence");
            }
            if (normalizedPath.contains("/nutrition/")) {
                return source.contains("com.fit.fitnessapp.auth.adapter.out.persistence");
            }
            if (normalizedPath.contains("/ai/")) {
                return source.contains("com.fit.fitnessapp.auth.adapter.out.persistence")
                        || source.contains("com.fit.fitnessapp.nutrition.adapter.out.persistence")
                        || source.contains("com.fit.fitnessapp.analytics.adapter.out.persistence");
            }
            if (normalizedPath.contains("/analytics/")) {
                return source.contains("com.fit.fitnessapp.auth.adapter.out.persistence")
                        || source.contains("com.fit.fitnessapp.nutrition.adapter.out.persistence")
                        || source.contains("com.fit.fitnessapp.ai.adapter.out.persistence");
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
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

    private Properties loadProperties(String path) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(path))) {
            properties.load(reader);
        }
        return properties;
    }
}
