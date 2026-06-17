package com.fit.fitnessapp.auth.infrastructure.utils;

import com.fit.fitnessapp.auth.infrastructure.config.SecurityProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/auth/login";
    private static final String REGISTER_PATH = "/auth/register";
    private static final int TOO_MANY_REQUESTS = 429;

    private final SecurityProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathWithinApplication(request);
        return !LOGIN_PATH.equals(path) && !REGISTER_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr() + ":" + pathWithinApplication(request);
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> newBucket());

        if (!bucket.tryConsume(1)) {
            response.setStatus(TOO_MANY_REQUESTS);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Too many authentication attempts\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Bucket newBucket() {
        SecurityProperties.AuthRateLimit rateLimit = properties.authRateLimit();
        Bandwidth limit = Bandwidth.classic(
                rateLimit.capacity(),
                Refill.intervally(rateLimit.capacity(), rateLimit.refillPeriod()));
        return Bucket.builder().addLimit(limit).build();
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        if (contextPath == null || contextPath.isBlank()) {
            return requestUri;
        }
        return requestUri.substring(contextPath.length());
    }
}
