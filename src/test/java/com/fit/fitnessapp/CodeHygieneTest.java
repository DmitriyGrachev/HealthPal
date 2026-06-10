package com.fit.fitnessapp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
}
