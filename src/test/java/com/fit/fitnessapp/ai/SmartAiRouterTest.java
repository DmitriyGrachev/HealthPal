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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmartAiRouter — fallback логика")
class SmartAiRouterTest {

    @Mock private AiModelPort openRouterPort;
    @Mock private AiModelPort geminiPort;
    @Mock private AiProperties aiProperties;
    @Mock private AiProperties.OpenRouterProperties openRouterProperties;

    private SmartAiRouter smartAiRouter;

    @BeforeEach
    void setUp() {
        smartAiRouter = new SmartAiRouter(openRouterPort, geminiPort, aiProperties);
        lenient().when(aiProperties.openrouter()).thenReturn(openRouterProperties);
    }

    @Test
    @DisplayName("Ошибка 429 (Rate Limit) -> переключение на вторую модель")
    void when429RateLimit_fallsBackToNextModel() {
        when(openRouterProperties.fallbackModels()).thenReturn(List.of("model-a", "model-b"));

        when(openRouterPort.generate(anyString(), eq("model-a")))
                .thenThrow(new AiUnavailableException("HTTP 429 Too Many Requests", new RuntimeException()));
        
        NutritionInsightResponse mockResponse = createMockResponse("Ответ от model-b");
        when(openRouterPort.generate(anyString(), eq("model-b")))
                .thenReturn(mockResponse);

        NutritionInsightResponse result = smartAiRouter.callWithFallback("Промпт");

        assertThat(result.summary()).isEqualTo("Ответ от model-b");
        verify(openRouterPort, times(2)).generate(anyString(), anyString());
        verifyNoInteractions(geminiPort);
    }

    @Test
    @DisplayName("Ошибка 401 (Auth) -> Немедленный Fallback на Gemini")
    void when401AuthError_abortsOpenRouterAndCallsGemini() {
        when(openRouterProperties.fallbackModels()).thenReturn(List.of("model-a", "model-b", "model-c"));

        when(openRouterPort.generate(anyString(), eq("model-a")))
                .thenThrow(new AiAuthException("HTTP 401 Unauthorized", new RuntimeException()));
        
        NutritionInsightResponse mockResponse = createMockResponse("Gemini Answer");
        when(geminiPort.generate(anyString()))
                .thenReturn(mockResponse);

        NutritionInsightResponse result = smartAiRouter.callWithFallback("Промпт");

        assertThat(result.summary()).isEqualTo("Gemini Answer");
        // OpenRouter вызван ровно 1 раз, потому что 401 прервал перебор
        verify(openRouterPort, times(1)).generate(anyString(), anyString());
        verify(geminiPort, times(1)).generate(anyString());
    }

    private NutritionInsightResponse createMockResponse(String summary) {
        return new NutritionInsightResponse(
                null, null, summary, summary,
                null, null,
                List.of(), List.of(), List.of(),
                1.0f, 1.0f
        );
    }
}
