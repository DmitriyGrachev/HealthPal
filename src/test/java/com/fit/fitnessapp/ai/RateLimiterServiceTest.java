package com.fit.fitnessapp.ai;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimiterService - Token Bucket Logic")
class RateLimiterServiceTest {

    private final RateLimiterService rateLimiterService = new RateLimiterService();

    @Test
    @DisplayName("Same userId should return the same bucket instance")
    void sameUserIdReturnsSameBucket() {
        Bucket first = rateLimiterService.resolveBucket(42L);
        Bucket second = rateLimiterService.resolveBucket(42L);

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("Different userIds should return different bucket instances")
    void differentUserIdsReturnDifferentBuckets() {
        Bucket bucket1 = rateLimiterService.resolveBucket(1L);
        Bucket bucket2 = rateLimiterService.resolveBucket(2L);

        assertThat(bucket1).isNotSameAs(bucket2);
    }

    @Test
    @DisplayName("New bucket should have tokens available immediately")
    void newBucketHasTokensAvailable() {
        Bucket bucket = rateLimiterService.resolveBucket(100L);

        assertThat(bucket.tryConsume(1)).isTrue();
    }

    @Test
    @DisplayName("Rate limit should be enforced at 5 requests per second")
    void rateLimitEnforcedAt5RequestsPerSecond() {
        Bucket bucket = rateLimiterService.resolveBucket(200L);

        // 5 requests pass
        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryConsume(1))
                    .as("Request #%d should be allowed", i + 1)
                    .isTrue();
        }

        // 6th request blocks
        assertThat(bucket.tryConsume(1))
                .as("6th request should be blocked by rate limit")
                .isFalse();
    }
}