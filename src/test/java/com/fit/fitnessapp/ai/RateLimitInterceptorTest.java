package com.fit.fitnessapp.ai;


import com.fit.fitnessapp.auth.CurrentUserApi;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitInterceptor — preHandle логика")
class RateLimitInterceptorTest {

    @Mock
    private RateLimiterService rateLimiterService;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private Bucket bucket;

    @InjectMocks
    private RateLimitInterceptor interceptor;

    @Test
    @DisplayName("Токен есть → запрос пропускается (true)")
    void whenTokenAvailable_returnsTrue() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(1L);
        when(rateLimiterService.resolveBucket(1L)).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verifyNoMoreInteractions(response);
    }

    @Test
    @DisplayName("Токенов нет → 429 и запрос блокируется (false)")
    void whenNoTokens_sendsTooManyRequestsAndReturnsFalse() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(1L);
        when(rateLimiterService.resolveBucket(1L)).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(false);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        verify(response).sendError(eq(HttpStatus.TOO_MANY_REQUESTS.value()), anyString());
    }

    @Test
    @DisplayName("Юзер не авторизован (исключение) → пропускаем запрос (true)")
    void whenUserNotAuthenticated_returnsTrue() throws Exception {
        when(currentUserApi.getCurrentUserId())
                .thenThrow(new RuntimeException("Not authenticated"));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verifyNoInteractions(rateLimiterService);
    }
}
