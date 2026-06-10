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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("OpenRouterAdapter - Exception Translation")
class OpenRouterAdapterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    private OpenRouterAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenRouterAdapter(chatClient);
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
    }

    @Test
    @DisplayName("401 Unauthorized should be translated to AiAuthException")
    void translates401ToAuthException() {
        when(requestSpec.call()).thenThrow(new RuntimeException("HTTP 401 Unauthorized"));

        assertThatThrownBy(() -> adapter.generate("Test prompt"))
                .isInstanceOf(AiAuthException.class);

        // Verify that we didn't try a second call for auth errors
        verify(chatClient, times(1)).prompt();
    }

    @Test
    @DisplayName("400 Bad Request should be translated to AiInvalidRequestException")
    void translates400ToInvalidRequestException() {
        when(requestSpec.call()).thenThrow(new RuntimeException("HTTP 400 Bad Request: Prompt too long"));

        assertThatThrownBy(() -> adapter.generate("Test prompt"))
                .isInstanceOf(AiInvalidRequestException.class);

        // Verify that we didn't try a second call for invalid request errors
        verify(chatClient, times(1)).prompt();
    }

    @Test
    @DisplayName("Timeout or 500 should be translated to AiUnavailableException")
    void translatesTimeoutToUnavailableException(CapturedOutput output) {
        when(requestSpec.call()).thenThrow(new RuntimeException("Timeout exception"));

        assertThatThrownBy(() -> adapter.generate("Test prompt"))
                .isInstanceOf(AiUnavailableException.class);

        assertThat(output).doesNotContain("java.lang.RuntimeException: Timeout exception");
    }
}

