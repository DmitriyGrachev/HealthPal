package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.application.port.out.AiModelPort;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.ai.exception.AiAuthException;
import com.fit.fitnessapp.ai.exception.AiInvalidRequestException;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SmartAiRouter {

    private static final Duration DEFAULT_UNAVAILABLE_COOLDOWN = Duration.ofSeconds(30);
    private static final int MAX_COOLDOWN_MULTIPLIER = 4;
    private static final String GEMINI_ROUTE_KEY = "gemini:default";

    private final AiModelPort openRouterPort;
    private final AiModelPort geminiPort;
    private final AiProperties aiProperties;
    private final Clock clock;
    private final Duration unavailableCooldown;
    private final Map<String, RouteFailure> routeFailures = new ConcurrentHashMap<>();

    @Autowired
    public SmartAiRouter(
            @Qualifier("openRouterPort") AiModelPort openRouterPort,
            @Qualifier("geminiPort") AiModelPort geminiPort,
            AiProperties aiProperties) {
        this(openRouterPort, geminiPort, aiProperties, Clock.systemUTC(), DEFAULT_UNAVAILABLE_COOLDOWN);
    }

    SmartAiRouter(
            AiModelPort openRouterPort,
            AiModelPort geminiPort,
            AiProperties aiProperties,
            Clock clock,
            Duration unavailableCooldown) {
        this.openRouterPort = openRouterPort;
        this.geminiPort = geminiPort;
        this.aiProperties = aiProperties;
        this.clock = clock;
        this.unavailableCooldown = unavailableCooldown;
    }

    public NutritionInsightResponse callWithFallback(String promptText) {
        List<String> models = aiProperties.openrouter().fallbackModels();
        AiProperties.ExecutionProperties configuredPolicy = aiProperties.execution();
        int maxAttempts = configuredPolicy != null
                ? configuredPolicy.maxProviderAttempts()
                : AiProperties.ExecutionProperties.defaults().maxProviderAttempts();
        int attempts = 0;

        for (String modelName : models) {
            if (attempts >= maxAttempts) {
                break;
            }
            String routeKey = openRouterRouteKey(modelName);
            if (isInCooldown(routeKey)) {
                log.info("Skipping OpenRouter model {} while provider cooldown is active.", modelName);
                continue;
            }

            log.info("Trying OpenRouter model: {}", modelName);
            attempts++;
            try {
                NutritionInsightResponse response = openRouterPort.generate(promptText, modelName);
                resetFailure(routeKey);
                return response;
            } catch (AiAuthException | AiInvalidRequestException e) {
                log.warn("Fatal OpenRouter error: {}. Stopping OpenRouter retry loop.", e.getMessage());
                break;
            } catch (AiUnavailableException e) {
                markUnavailable(routeKey);
                log.warn("OpenRouter model {} is unavailable: {}. Trying next model.", modelName, e.getMessage());
            }
        }

        if (attempts >= maxAttempts) {
            throw new AiUnavailableException(
                    "AI provider attempt limit reached",
                    new IllegalStateException("Maximum provider attempts: " + maxAttempts));
        }

        log.warn("OpenRouter is unavailable. Switching to Gemini fallback.");
        if (isInCooldown(GEMINI_ROUTE_KEY)) {
            log.warn("Gemini fallback is unavailable because provider cooldown is active.");
            throw new AiUnavailableException(
                    "All AI providers are unavailable",
                    new IllegalStateException("Gemini fallback is cooling down")
            );
        }

        try {
            attempts++;
            NutritionInsightResponse response = geminiPort.generate(promptText);
            resetFailure(GEMINI_ROUTE_KEY);
            return response;
        } catch (AiUnavailableException e) {
            markUnavailable(GEMINI_ROUTE_KEY);
            log.warn("Gemini fallback is unavailable: {}", e.getMessage());
            throw new AiUnavailableException("All AI providers are unavailable", e);
        }
    }

    private boolean isInCooldown(String routeKey) {
        RouteFailure failure = routeFailures.get(routeKey);
        return failure != null && clock.instant().isBefore(failure.unavailableUntil());
    }

    private void markUnavailable(String routeKey) {
        RouteFailure previous = routeFailures.get(routeKey);
        int failures = previous == null ? 1 : previous.consecutiveFailures() + 1;
        int multiplier = Math.min(failures, MAX_COOLDOWN_MULTIPLIER);
        Instant unavailableUntil = clock.instant().plus(unavailableCooldown.multipliedBy(multiplier));
        routeFailures.put(routeKey, new RouteFailure(failures, unavailableUntil));
    }

    private void resetFailure(String routeKey) {
        routeFailures.remove(routeKey);
    }

    private static String openRouterRouteKey(String modelName) {
        return "openrouter:" + modelName;
    }

    private record RouteFailure(int consecutiveFailures, Instant unavailableUntil) {
    }
}
