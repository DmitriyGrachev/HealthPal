package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.auth.CurrentUserApi;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimiterService rateLimiterService;
    private final CurrentUserApi currentUserApi;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId;
        try {
            userId = currentUserApi.getCurrentUserId();
        } catch (Exception e) {
            log.warn("AI rate limit rejected request because current user could not be resolved: {}", e.getMessage());
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Authentication required for AI requests.");
            return false;
        }

        Bucket tokenBucket = rateLimiterService.resolveBucket(userId);
        if (tokenBucket.tryConsume(1)) {
            return true;
        }

        response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too many requests. Please wait.");
        return false;
    }
}
