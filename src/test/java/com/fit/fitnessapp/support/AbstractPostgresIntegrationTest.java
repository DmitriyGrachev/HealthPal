package com.fit.fitnessapp.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@Tag("integration")
@SpringBootTest
public abstract class AbstractPostgresIntegrationTest {

    private static final String PGVECTOR_IMAGE = "pgvector/pgvector:pg16";

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("fitnessapp_test")
            .withUsername("fitness")
            .withPassword("fitness");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("spring.data.jdbc.repositories.enabled", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");

        registry.add("fitness.app.secret", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("fitness.app.jwt-expiration", () -> "PT1H");
        registry.add("fitness.app.cors.allowed-origins", () -> "http://localhost:3000");
        registry.add("fitness.app.auth-rate-limit.capacity", () -> "100");
        registry.add("fitness.app.auth-rate-limit.refill-period", () -> "PT1M");

        registry.add("fatsecret.consumer-key", () -> "test-consumer-key");
        registry.add("fatsecret.consumer-secret", () -> "test-consumer-secret");
        registry.add("fatsecret.client-id", () -> "test-client-id");
        registry.add("fatsecret.client-secret", () -> "test-client-secret");
        registry.add("fatsecret.redirect-uri", () -> "http://localhost/test/redirect");
        registry.add("fatsecret.callback-url", () -> "http://localhost/test/callback");
        registry.add("fatsecret.config-check.enabled", () -> "false");
        registry.add("fatsecret.token-encryption.key", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

        registry.add("telegram.bot.username", () -> "test_bot");
        registry.add("telegram.bot.token", () -> "0000000000:test-token");
        registry.add("telegram.bot.enabled", () -> "false");

        registry.add("spring.ai.openai.api-key", () -> "test-openai-key");
        registry.add("spring.ai.openai.base-url", () -> "http://localhost/openrouter");
        registry.add("spring.ai.google.genai.api-key", () -> "test-gemini-key");
        registry.add("spring.ai.google.genai.embedding.enabled", () -> "false");
        registry.add("spring.ai.openai.embedding.options.model", () -> "test-embedding-model");
        registry.add("spring.ai.vectorstore.pgvector.initialize-schema", () -> "false");
        registry.add("spring.ai.vectorstore.pgvector.table-name", () -> "user_memory");
        registry.add("spring.ai.vectorstore.pgvector.index-type", () -> "NONE");
        registry.add("spring.ai.vectorstore.pgvector.dimension", () -> "2048");
        registry.add("app.ai.allow-sensitive-external-egress", () -> "true");
        registry.add("memory.cleanup.enabled", () -> "false");
    }
}
