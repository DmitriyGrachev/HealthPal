package com.fit.fitnessapp.ai;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String defaultProvider,
        OpenRouterProperties openrouter,
        Map<String, ProviderSettings> providers,
        @NotNull String DAILY_INSIGHT_MODEL,
        @NotNull String QUICK_ANALYSIS_MODEL
) {
    public record ProviderSettings(boolean enabled) {}

    public record OpenRouterProperties(List<String> fallbackModels) {}
}

