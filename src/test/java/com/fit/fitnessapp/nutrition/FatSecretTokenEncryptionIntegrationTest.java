package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FatSecretTokenEncryptionIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private NutritionCommandPort nutritionCommandPort;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void saveTokenStoresEncryptedDatabaseColumnsAndGetTokenReturnsPlaintext() {
        Long userId = insertUser();
        FatSecretToken token = new FatSecretToken("access-token", "access-secret");

        nutritionCommandPort.saveToken(userId, token);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT access_token, access_token_secret FROM fatsecret_connection WHERE user_id = ?",
                userId);
        assertThat(row.get("access_token")).isNotEqualTo(token.accessToken());
        assertThat(row.get("access_token").toString()).startsWith("enc:v1:");
        assertThat(row.get("access_token_secret")).isNotEqualTo(token.accessTokenSecret());
        assertThat(row.get("access_token_secret").toString()).startsWith("enc:v1:");

        assertThat(nutritionCommandPort.getToken(userId)).contains(token);
    }

    private Long insertUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO users (username, email, password)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "fatsecret-" + suffix,
                "fatsecret-" + suffix + "@example.test",
                "{noop}password");
    }
}
