package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.application.port.out.AiModelPort;
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
                .thenThrow(new RuntimeException("HTTP 429 Too Many Requests"));
        when(openRouterPort.generate(anyString(), eq("model-b")))
                .thenReturn("Ответ от model-b");

        String result = smartAiRouter.callWithFallback("Промпт");

        assertThat(result).isEqualTo("Ответ от model-b");
        verify(openRouterPort, times(2)).generate(anyString(), anyString());
        verifyNoInteractions(geminiPort);
    }

    @Test
    @DisplayName("Ошибка 401 (Auth) -> Немедленный Fallback на Gemini")
    void when401AuthError_abortsOpenRouterAndCallsGemini() {
        when(openRouterProperties.fallbackModels()).thenReturn(List.of("model-a", "model-b", "model-c"));

        when(openRouterPort.generate(anyString(), eq("model-a")))
                .thenThrow(new RuntimeException("HTTP 401 Unauthorized"));
        when(geminiPort.generate(anyString(), isNull()))
                .thenReturn("Gemini Answer");

        String result = smartAiRouter.callWithFallback("Промпт");

        assertThat(result).isEqualTo("Gemini Answer");
        // OpenRouter вызван ровно 1 раз, потому что 401 прервал перебор
        verify(openRouterPort, times(1)).generate(anyString(), anyString());
        verify(geminiPort, times(1)).generate(anyString(), isNull());
    }
}
