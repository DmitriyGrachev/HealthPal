package com.fit.fitnessapp.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String defaultProvider,
        Map<String, ProviderSettings> providers
) {
    public record ProviderSettings(boolean enabled) {}

    public boolean isProviderEnabled(String providerName) {
        var settings = providers.get(providerName);
        return settings != null && settings.enabled();
    }
}

