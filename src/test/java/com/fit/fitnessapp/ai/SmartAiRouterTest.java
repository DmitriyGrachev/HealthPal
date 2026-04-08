package com.fit.fitnessapp.ai;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmartAiRouter — fallback логика")
class SmartAiRouterTest {

    @Mock private ChatClient openRouterClient;
    @Mock private ChatClient googleClient;
    @Mock private AiProperties aiProperties;
    @Mock private AiProperties.OpenRouterProperties openRouterProperties;

    // client.prompt().user(...).options(...).call().content()
    @Mock private ChatClient.ChatClientRequestSpec promptSpec;
    @Mock private ChatClient.ChatClientRequestSpec userSpec;
    @Mock private ChatClient.ChatClientRequestSpec optionsSpec;
    @Mock private ChatClient.CallResponseSpec callSpec;

    private SmartAiRouter smartAiRouter;

    @BeforeEach
    void setUp() {
        smartAiRouter = new SmartAiRouter(openRouterClient, googleClient, aiProperties);

        lenient().when(aiProperties.openrouter()).thenReturn(openRouterProperties);
        lenient().when(openRouterClient.prompt()).thenReturn(promptSpec);
        lenient().when(promptSpec.user(anyString())).thenReturn(userSpec);
        lenient().when(userSpec.options(any())).thenReturn(optionsSpec);
        lenient().when(optionsSpec.call()).thenReturn(callSpec);
    }

    @Test
    @DisplayName("Первая модель отвечает — возвращаем её ответ")
    void whenFirstModelResponds_returnsItsContent() {
        when(openRouterProperties.fallbackModels())
                .thenReturn(List.of("model-a", "model-b"));
        when(callSpec.content()).thenReturn("Отличный результат от model-a");

        String result = smartAiRouter.callWithFallback("Тестовый промпт");

        assertThat(result).isEqualTo("Отличный результат от model-a");
        // Должна быть вызвана только одна модель
        verify(openRouterClient, times(1)).prompt();
        verifyNoInteractions(googleClient);
    }

    @Test
    @DisplayName("Первая модель упала — переключаемся на вторую")
    void whenFirstModelFails_fallsBackToSecondModel() {
        when(openRouterProperties.fallbackModels())
                .thenReturn(List.of("model-a", "model-b"));

        when(callSpec.content())
                .thenThrow(new RuntimeException("model-a timeout"))
                .thenReturn("Ответ от model-b");

        String result = smartAiRouter.callWithFallback("Промпт");

        assertThat(result).isEqualTo("Ответ от model-b");
        verify(openRouterClient, times(2)).prompt();
        verifyNoInteractions(googleClient);
    }

    @Test
    @DisplayName("Все OpenRouter модели упали — используем Google Gemini")
    void whenAllOpenRouterModelsFail_fallsBackToGemini() {
        when(openRouterProperties.fallbackModels())
                .thenReturn(List.of("model-a", "model-b"));
        when(callSpec.content())
                .thenThrow(new RuntimeException("все упали"));

        // Gemini
        ChatClient.ChatClientRequestSpec geminiPromptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec geminiCallSpec = mock(ChatClient.CallResponseSpec.class);
        when(googleClient.prompt()).thenReturn(geminiPromptSpec);
        when(geminiPromptSpec.user(anyString())).thenReturn(geminiPromptSpec);
        when(geminiPromptSpec.call()).thenReturn(geminiCallSpec);
        when(geminiCallSpec.content()).thenReturn("Gemini спасает положение");

        String result = smartAiRouter.callWithFallback("Промпт");

        assertThat(result).isEqualTo("Gemini спасает положение");
        verify(openRouterClient, times(2)).prompt(); // перебрали обе модели
        verify(googleClient, times(1)).prompt();
    }

    @Test
    @DisplayName("Все провайдеры упали — бросаем RuntimeException")
    void whenAllProvidersFail_throwsRuntimeException() {
        when(openRouterProperties.fallbackModels())
                .thenReturn(List.of("model-a"));
        when(callSpec.content()).thenThrow(new RuntimeException("OpenRouter down"));

        ChatClient.ChatClientRequestSpec geminiSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec geminiCallSpec = mock(ChatClient.CallResponseSpec.class);
        when(googleClient.prompt()).thenReturn(geminiSpec);
        when(geminiSpec.user(anyString())).thenReturn(geminiSpec);
        when(geminiSpec.call()).thenReturn(geminiCallSpec);
        when(geminiCallSpec.content()).thenThrow(new RuntimeException("Gemini down"));

        assertThatThrownBy(() -> smartAiRouter.callWithFallback("Промпт"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("All AI providers are down");
    }

    @Test
    @DisplayName("Пустой список моделей — сразу переходим к Gemini")
    void whenNoModelsConfigured_goesDirectlyToGemini() {
        when(openRouterProperties.fallbackModels()).thenReturn(List.of());

        ChatClient.ChatClientRequestSpec geminiSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec geminiCallSpec = mock(ChatClient.CallResponseSpec.class);
        when(googleClient.prompt()).thenReturn(geminiSpec);
        when(geminiSpec.user(anyString())).thenReturn(geminiSpec);
        when(geminiSpec.call()).thenReturn(geminiCallSpec);
        when(geminiCallSpec.content()).thenReturn("Gemini отвечает");

        String result = smartAiRouter.callWithFallback("Промпт");

        assertThat(result).isEqualTo("Gemini отвечает");
        verifyNoInteractions(openRouterClient);
    }
}
