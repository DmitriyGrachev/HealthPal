package com.fit.fitnessapp.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = "fitness.app")
public record SecurityProperties(
        String secret,
        Duration jwtExpiration,
        Cors cors,
        AuthRateLimit authRateLimit) {

    public SecurityProperties {
        Objects.requireNonNull(secret, "fitness.app.secret must be configured");
        Objects.requireNonNull(jwtExpiration, "fitness.app.jwt-expiration must be configured");
        if (cors == null) {
            cors = new Cors(List.of());
        }
        if (authRateLimit == null) {
            authRateLimit = new AuthRateLimit(5, Duration.ofMinutes(1));
        }
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            if (allowedOrigins == null) {
                allowedOrigins = List.of();
            }
        }
    }

    public record AuthRateLimit(long capacity, Duration refillPeriod) {
        public AuthRateLimit {
            if (capacity < 1) {
                capacity = 1;
            }
            if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
                refillPeriod = Duration.ofMinutes(1);
            }
        }
    }
}
