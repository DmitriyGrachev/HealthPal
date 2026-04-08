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

@DisplayName("RateLimiterService — управление бакетами")
class RateLimiterServiceTest {

    private final RateLimiterService rateLimiterService = new RateLimiterService();

    @Test
    @DisplayName("Один и тот же userId → один и тот же Bucket (кэш работает)")
    void sameuserIdReturnsSameBucket() {
        Bucket first = rateLimiterService.resolveBucket(42L);
        Bucket second = rateLimiterService.resolveBucket(42L);

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("Разные userId → разные Bucket-ы")
    void differentUserIdsReturnDifferentBuckets() {
        Bucket bucket1 = rateLimiterService.resolveBucket(1L);
        Bucket bucket2 = rateLimiterService.resolveBucket(2L);

        assertThat(bucket1).isNotSameAs(bucket2);
    }

    @Test
    @DisplayName("Новый Bucket содержит токены (можно делать запросы)")
    void newBucketHasTokensAvailable() {
        Bucket bucket = rateLimiterService.resolveBucket(100L);

        assertThat(bucket.tryConsume(1)).isTrue();
    }

    @Test
    @DisplayName("Лимит 5 запросов в секунду — 5-й проходит, 6-й блокируется")
    void rateLimitEnforcedAt5RequestsPerSecond() {
        Bucket bucket = rateLimiterService.resolveBucket(200L);

        // 5 pass
        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryConsume(1))
                    .as("Запрос #%d должен пройти", i + 1)
                    .isTrue();
        }

        // 6 block
        assertThat(bucket.tryConsume(1))
                .as("6-й запрос должен быть заблокирован")
                .isFalse();
    }
}