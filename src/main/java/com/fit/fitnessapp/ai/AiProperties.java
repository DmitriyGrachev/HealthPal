package com.fit.fitnessapp.ai;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String defaultProvider,
        OpenRouterProperties openrouter,
        Map<String, ProviderSettings> providers,
        @NotNull String DAILY_INSIGHT_MODEL,
        @NotNull String QUICK_ANALYSIS_MODEL,
        ExecutionProperties execution,
        boolean allowSensitiveExternalEgress
) {
    public record ProviderSettings(boolean enabled) {}

    public record OpenRouterProperties(List<String> fallbackModels) {}

    public ExecutionProperties executionOrDefaults() {
        return execution != null ? execution : ExecutionProperties.defaults();
    }

    public record ExecutionProperties(
            Duration overallDeadline,
            int maxProviderAttempts,
            int globalConcurrency,
            int perUserConcurrency,
            long globalHourlyTokenBudget,
            long perUserHourlyTokenBudget,
            int reservedOutputTokens
    ) {
        public ExecutionProperties {
            overallDeadline = overallDeadline == null ? Duration.ofSeconds(30) : overallDeadline;
            maxProviderAttempts = positiveOrDefault(maxProviderAttempts, 3);
            globalConcurrency = positiveOrDefault(globalConcurrency, 8);
            perUserConcurrency = positiveOrDefault(perUserConcurrency, 1);
            globalHourlyTokenBudget = positiveOrDefault(globalHourlyTokenBudget, 1_000_000L);
            perUserHourlyTokenBudget = positiveOrDefault(perUserHourlyTokenBudget, 50_000L);
            reservedOutputTokens = positiveOrDefault(reservedOutputTokens, 2_000);
        }

        static ExecutionProperties defaults() {
            return new ExecutionProperties(null, 0, 0, 0, 0, 0, 0);
        }

        private static int positiveOrDefault(int value, int fallback) {
            return value > 0 ? value : fallback;
        }

        private static long positiveOrDefault(long value, long fallback) {
            return value > 0 ? value : fallback;
        }
    }
}

