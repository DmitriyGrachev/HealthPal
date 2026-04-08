package com.fit.fitnessapp.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String defaultProvider,
        OpenRouterProperties openrouter,
        Map<String, ProviderSettings> providers
) {
    public record ProviderSettings(boolean enabled) {}

    public record OpenRouterProperties(List<String> fallbackModels) {}
}

