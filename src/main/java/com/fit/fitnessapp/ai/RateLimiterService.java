package com.fit.fitnessapp.ai;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService {

    private final Cache<Long, Bucket> cache = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(10_000)
            .build();

    public Bucket resolveBucket(Long userId) {
        return cache.get(userId, this::newBucket);
    }

    private Bucket newBucket(Long userId) {
        // Лимит: 5 запросов в секунду максимум
        Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofSeconds(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}