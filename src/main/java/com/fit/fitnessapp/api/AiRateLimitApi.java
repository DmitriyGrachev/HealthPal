package com.fit.fitnessapp.api;

public interface AiRateLimitApi {
    boolean tryConsume(Long userId);
}
