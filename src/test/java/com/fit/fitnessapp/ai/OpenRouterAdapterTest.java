package com.fit.fitnessapp.ai;
import com.fit.fitnessapp.ai.adapter.out.OpenRouterAdapter;
import com.fit.fitnessapp.ai.exception.AiAuthException;
import com.fit.fitnessapp.ai.exception.AiInvalidRequestException;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OpenRouterAdapter — трансляция исключений")
class OpenRouterAdapterTest {

    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;

    private OpenRouterAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenRouterAdapter(chatClient);
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
    }

    @Test
    @DisplayName("Ошибка 401 превращается в AiAuthException")
    void translates401ToAuthException() {
        when(requestSpec.call()).thenThrow(new RuntimeException("HTTP 401 Unauthorized"));

        assertThatThrownBy(() -> adapter.generate("Test prompt"))
                .isInstanceOf(AiAuthException.class)
                .hasMessageContaining("Ошибка авторизации");
    }

    @Test
    @DisplayName("Ошибка 400 превращается в AiInvalidRequestException")
    void translates400ToInvalidRequestException() {
        when(requestSpec.call()).thenThrow(new RuntimeException("HTTP 400 Bad Request: Prompt too long"));

        assertThatThrownBy(() -> adapter.generate("Test prompt"))
                .isInstanceOf(AiInvalidRequestException.class)
                .hasMessageContaining("Невалидный промпт");
    }

    @Test
    @DisplayName("Таймаут или 500 превращается в AiUnavailableException")
    void translatesTimeoutToUnavailableException() {
        when(requestSpec.call()).thenThrow(new RuntimeException("Timeout exception"));

        assertThatThrownBy(() -> adapter.generate("Test prompt"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("недоступен");
    }
}
