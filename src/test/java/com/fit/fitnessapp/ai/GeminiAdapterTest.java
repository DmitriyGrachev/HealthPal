package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.adapter.out.GeminiAdapter;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;
import com.fit.fitnessapp.ai.exception.AiRateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class GeminiAdapterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    private GeminiAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new GeminiAdapter(chatClient);
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
    }

    @Test
    void unavailableGeminiDoesNotLogParsingFailureStackTrace(CapturedOutput output) {
        when(requestSpec.call()).thenThrow(new RuntimeException("Gemini timeout"));

        assertThatThrownBy(() -> adapter.generate("prompt"))
                .isInstanceOf(AiUnavailableException.class);

        assertThat(output).doesNotContain("java.lang.RuntimeException: Gemini timeout");
    }

    @Test
    void translatesTyped429WithoutSecondProviderCall() {
        when(requestSpec.call()).thenThrow(HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8));

        assertThatThrownBy(() -> adapter.generate("prompt"))
                .isExactlyInstanceOf(AiRateLimitException.class);
    }
}
