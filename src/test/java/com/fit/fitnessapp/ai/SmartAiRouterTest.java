package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.application.port.out.AiModelPort;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.ai.exception.AiAuthException;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("SmartAiRouter - Fallback Logic")
class SmartAiRouterTest {

    @Mock
    private AiModelPort openRouterPort;

    @Mock
    private AiModelPort geminiPort;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private AiProperties.OpenRouterProperties openRouterProperties;

    private SmartAiRouter smartAiRouter;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-07-05T00:00:00Z"));
        smartAiRouter = new SmartAiRouter(
                openRouterPort,
                geminiPort,
                aiProperties,
                clock,
                Duration.ofSeconds(30)
        );
        lenient().when(aiProperties.openrouter()).thenReturn(openRouterProperties);
    }

    @Test
    @DisplayName("When 429 Rate Limit occurs, should fall back to the next model in OpenRouter")
    void when429RateLimit_fallsBackToNextModel() {
        when(openRouterProperties.fallbackModels()).thenReturn(List.of("model-a", "model-b"));

        when(openRouterPort.generate(anyString(), eq("model-a")))
                .thenThrow(new AiUnavailableException("HTTP 429 Too Many Requests", new RuntimeException()));

        NutritionInsightResponse mockResponse = createMockResponse("Answer from model-b");
        when(openRouterPort.generate(anyString(), eq("model-b")))
                .thenReturn(mockResponse);

        NutritionInsightResponse result = smartAiRouter.callWithFallback("Prompt");

        assertThat(result.summary()).isEqualTo("Answer from model-b");
        verify(openRouterPort, times(2)).generate(anyString(), anyString());
        verifyNoInteractions(geminiPort);
    }

    @Test
    @DisplayName("When 401 Auth Error occurs, should immediately fall back to Gemini")
    void when401AuthError_abortsOpenRouterAndCallsGemini(CapturedOutput output) {
        when(openRouterProperties.fallbackModels()).thenReturn(List.of("model-a", "model-b", "model-c"));

        when(openRouterPort.generate(anyString(), eq("model-a")))
                .thenThrow(new AiAuthException("HTTP 401 Unauthorized", new RuntimeException()));

        NutritionInsightResponse mockResponse = createMockResponse("Gemini Answer");
        when(geminiPort.generate(anyString()))
                .thenReturn(mockResponse);

        NutritionInsightResponse result = smartAiRouter.callWithFallback("Prompt");

        assertThat(result.summary()).isEqualTo("Gemini Answer");
        // OpenRouter is called exactly once because 401 aborts the retry loop
        verify(openRouterPort, times(1)).generate(anyString(), anyString());
        verify(geminiPort, times(1)).generate(anyString());
        assertThat(output).doesNotContain(" ERROR ");
    }

    @Test
    @DisplayName("When first OpenRouter model succeeds, should not try other models")
    void whenFirstModelSucceeds_noFallbackNeeded() {
        when(openRouterProperties.fallbackModels()).thenReturn(List.of("model-a", "model-b"));

        NutritionInsightResponse mockResponse = createMockResponse("Answer from model-a");
        when(openRouterPort.generate(anyString(), eq("model-a")))
                .thenReturn(mockResponse);

        NutritionInsightResponse result = smartAiRouter.callWithFallback("Prompt");

        assertThat(result.summary()).isEqualTo("Answer from model-a");
        verify(openRouterPort, times(1)).generate(anyString(), anyString());
        verifyNoInteractions(geminiPort);
    }

    @Test
    @DisplayName("When all OpenRouter models are unavailable, should fall back to Gemini")
    void whenAllOpenRouterModelsUnavailable_fallsBackToGemini() {
        when(openRouterProperties.fallbackModels()).thenReturn(List.of("model-a", "model-b"));

        when(openRouterPort.generate(anyString(), eq("model-a")))
                .thenThrow(new AiUnavailableException("model-a down", new RuntimeException()));
        when(openRouterPort.generate(anyString(), eq("model-b")))
                .thenThrow(new AiUnavailableException("model-b down", new RuntimeException()));

        NutritionInsightResponse geminiResponse = createMockResponse("Gemini fallback answer");
        when(geminiPort.generate(anyString())).thenReturn(geminiResponse);

        NutritionInsightResponse result = smartAiRouter.callWithFallback("Prompt");

        assertThat(result.summary()).isEqualTo("Gemini fallback answer");
        verify(openRouterPort, times(2)).generate(anyString(), anyString());
        verify(geminiPort, times(1)).generate(anyString());
    }

    @Test
    @DisplayName("When ALL providers are down (OpenRouter + Gemini), should throw RuntimeException")
    void whenAllProvidersDown_throwsRuntimeException(CapturedOutput output) {
        when(openRouterProperties.fallbackModels()).thenReturn(List.of("model-a"));

        when(openRouterPort.generate(anyString(), eq("model-a")))
                .thenThrow(new AiUnavailableException("model-a down", new RuntimeException()));
        when(geminiPort.generate(anyString()))
                .thenThrow(new AiUnavailableException("Gemini down", new RuntimeException()));

        assertThatThrownBy(() -> smartAiRouter.callWithFallback("Prompt"))
                .isInstanceOf(RuntimeException.class);

        verify(openRouterPort, times(1)).generate(anyString(), anyString());
        verify(geminiPort, times(1)).generate(anyString());
        assertThat(output).doesNotContain("com.fit.fitnessapp.ai.exception.AiUnavailableException: Gemini down");
        assertThat(output).doesNotContain(" ERROR ");
    }

    @Test
    @DisplayName("When model is cooling down, should skip it on repeated call")
    void repeatedCallInsideCooldown_skipsRecentlyUnavailableModel() {
        when(openRouterProperties.fallbackModels()).thenReturn(List.of("model-a", "model-b"));

        when(openRouterPort.generate(anyString(), eq("model-a")))
                .thenThrow(new AiUnavailableException("model-a down", new RuntimeException()));

        NutritionInsightResponse modelBResponse = createMockResponse("Answer from model-b");
        when(openRouterPort.generate(anyString(), eq("model-b")))
                .thenReturn(modelBResponse);

        NutritionInsightResponse firstResult = smartAiRouter.callWithFallback("Prompt 1");
        NutritionInsightResponse secondResult = smartAiRouter.callWithFallback("Prompt 2");

        assertThat(firstResult.summary()).isEqualTo("Answer from model-b");
        assertThat(secondResult.summary()).isEqualTo("Answer from model-b");
        verify(openRouterPort, times(1)).generate(anyString(), eq("model-a"));
        verify(openRouterPort, times(2)).generate(anyString(), eq("model-b"));
        verifyNoInteractions(geminiPort);
    }

    @Test
    @DisplayName("When recovered model succeeds, should reset backoff state")
    void successfulCall_resetsBackoffState() {
        when(openRouterProperties.fallbackModels()).thenReturn(List.of("model-a", "model-b"));

        NutritionInsightResponse modelAResponse = createMockResponse("Answer from model-a");
        when(openRouterPort.generate(anyString(), eq("model-a")))
                .thenThrow(new AiUnavailableException("first outage", new RuntimeException()))
                .thenReturn(modelAResponse)
                .thenThrow(new AiUnavailableException("second outage", new RuntimeException()))
                .thenReturn(modelAResponse);

        NutritionInsightResponse modelBResponse = createMockResponse("Answer from model-b");
        when(openRouterPort.generate(anyString(), eq("model-b")))
                .thenReturn(modelBResponse);

        assertThat(smartAiRouter.callWithFallback("Prompt 1").summary()).isEqualTo("Answer from model-b");

        clock.advance(Duration.ofSeconds(31));
        assertThat(smartAiRouter.callWithFallback("Prompt 2").summary()).isEqualTo("Answer from model-a");

        assertThat(smartAiRouter.callWithFallback("Prompt 3").summary()).isEqualTo("Answer from model-b");

        clock.advance(Duration.ofSeconds(31));
        assertThat(smartAiRouter.callWithFallback("Prompt 4").summary()).isEqualTo("Answer from model-a");

        verify(openRouterPort, times(4)).generate(anyString(), eq("model-a"));
        verify(openRouterPort, times(2)).generate(anyString(), eq("model-b"));
        verifyNoInteractions(geminiPort);
    }

    private NutritionInsightResponse createMockResponse(String summary) {
        return new NutritionInsightResponse(
                null, null, summary, summary,
                null, null,
                List.of(), List.of(), List.of(),
                1.0f, 1.0f
        );
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}

