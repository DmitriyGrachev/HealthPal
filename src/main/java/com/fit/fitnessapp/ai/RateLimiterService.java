package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.api.AiRateLimitApi;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService implements AiRateLimitApi {

    private final Cache<Long, Bucket> cache = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(10_000)
            .build();

    public Bucket resolveBucket(Long userId) {
        return cache.get(userId, this::newBucket);
    }

    @Override
    public boolean tryConsume(Long userId) {
        return resolveBucket(userId).tryConsume(1);
    }

    private Bucket newBucket(Long userId) {
        // Limit: at most 5 requests per second.
        Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofSeconds(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
