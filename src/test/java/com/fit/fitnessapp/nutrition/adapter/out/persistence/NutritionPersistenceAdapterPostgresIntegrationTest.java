package com.fit.fitnessapp.nutrition.adapter.out.persistence;

import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.ProviderDataIdentifier;
import com.fit.fitnessapp.nutrition.domain.ProviderDataRetentionPolicy;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class NutritionPersistenceAdapterPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private NutritionCommandPort nutritionCommandPort;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanNutritionTables() {
        jdbc.update("DELETE FROM fatsecret_provider_identifiers");
        jdbc.update("DELETE FROM fatsecret_food");
        jdbc.update("DELETE FROM fatsecret_day");
    }

    @Test
    void migrationsCreateSplitNutritionHashColumns() {
        Set<String> columns = Set.copyOf(jdbc.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_name = 'fatsecret_day'
                  AND column_name IN ('external_hash', 'summary_hash', 'entries_hash')
                """, String.class));

        assertThat(columns)
                .containsExactlyInAnyOrder("external_hash", "summary_hash", "entries_hash");
    }

    @Test
    void v11MigrationBackfillsSplitHashesFromExistingExternalHash() {
        String schema = "nutrition_migration_" + UUID.randomUUID().toString().replace("-", "");
        String externalHash = "legacy-external-hash";
        jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");

        try {
            migrateSchema(schema, "10");
            LocalDate date = LocalDate.of(2026, 7, 6);
            jdbc.update("""
                    INSERT INTO %s.fatsecret_day
                        (user_id, date, date_int, calories, protein, fat, carbohydrate, external_hash)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.formatted(schema),
                    42L,
                    date,
                    (int) date.toEpochDay(),
                    220.0,
                    25.0,
                    4.0,
                    18.0,
                    externalHash
            );

            migrateSchema(schema, "12");

            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT external_hash, summary_hash, entries_hash FROM " + schema + ".fatsecret_day WHERE user_id = ?",
                    42L
            );
            assertThat(row)
                    .containsEntry("external_hash", externalHash)
                    .containsEntry("summary_hash", externalHash)
                    .containsEntry("entries_hash", externalHash);
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void identifierOnlyPersistenceIsIdempotentAndFencedAcrossReconnect() {
        long userId = insertUser("nutrition-" + UUID.randomUUID());
        Set<ProviderDataIdentifier> identifiers = Set.of(
                ProviderDataRetentionPolicy.identifier("food_id", "10"),
                ProviderDataRetentionPolicy.identifier("food_entry_id", "100"));

        nutritionCommandPort.saveToken(userId, new FatSecretToken("first-token", "first-secret"));
        var firstConnection = nutritionCommandPort.getConnectionSnapshot(userId).orElseThrow();

        assertThat(nutritionCommandPort.saveProviderIdentifiers(firstConnection, identifiers)).isEqualTo(2);
        assertThat(nutritionCommandPort.saveProviderIdentifiers(firstConnection, identifiers)).isEqualTo(2);
        assertThat(nutritionCommandPort.getProviderIdentifiers(userId))
                .containsExactlyInAnyOrderElementsOf(identifiers);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fatsecret_day WHERE user_id = ?", Long.class, userId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fatsecret_food", Long.class)).isZero();

        nutritionCommandPort.saveToken(userId, new FatSecretToken("second-token", "second-secret"));
        var secondConnection = nutritionCommandPort.getConnectionSnapshot(userId).orElseThrow();

        assertThat(secondConnection.connectionEpoch()).isNotEqualTo(firstConnection.connectionEpoch());
        assertThat(nutritionCommandPort.getProviderIdentifiers(userId)).isEmpty();
        assertThat(nutritionCommandPort.saveProviderIdentifiers(firstConnection, identifiers)).isZero();

        ProviderDataIdentifier servingId = ProviderDataRetentionPolicy.identifier("serving_id", "200");
        assertThat(nutritionCommandPort.saveProviderIdentifiers(secondConnection, Set.of(servingId))).isOne();
        assertThat(nutritionCommandPort.getProviderIdentifiers(userId)).containsExactly(servingId);
    }

    @Test
    void lateIdentifierWriteWaitsForDisconnectOwnerFenceAndCannotRepopulate() throws Exception {
        long userId = insertUser("nutrition-fence-" + UUID.randomUUID());
        nutritionCommandPort.saveToken(userId, new FatSecretToken("token", "secret"));
        var connection = nutritionCommandPort.getConnectionSnapshot(userId).orElseThrow();
        ProviderDataIdentifier foodId = ProviderDataRetentionPolicy.identifier("food_id", "404");
        CountDownLatch disconnectHoldingOwner = new CountDownLatch(1);
        CountDownLatch allowDisconnectCommit = new CountDownLatch(1);
        CountDownLatch lateWriteStarted = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var disconnect = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
                jdbc.update("DELETE FROM fatsecret_connection WHERE user_id = ?", userId);
                disconnectHoldingOwner.countDown();
                await(allowDisconnectCommit, "disconnect release timed out");
            }));

            assertThat(disconnectHoldingOwner.await(5, TimeUnit.SECONDS)).isTrue();
            var lateWrite = executor.submit(() -> {
                lateWriteStarted.countDown();
                return nutritionCommandPort.saveProviderIdentifiers(connection, Set.of(foodId));
            });
            assertThat(lateWriteStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            assertThat(lateWrite.isDone()).isFalse();

            allowDisconnectCommit.countDown();
            disconnect.get(5, TimeUnit.SECONDS);
            assertThat(lateWrite.get(5, TimeUnit.SECONDS)).isZero();
        } finally {
            allowDisconnectCommit.countDown();
        }

        assertThat(nutritionCommandPort.getProviderIdentifiers(userId)).isEmpty();
    }

    private long insertUser(String username) {
        String safeUsername = username.length() > 32 ? username.substring(0, 32) : username;
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                safeUsername,
                safeUsername + "@t.test",
                "{noop}password"
        );
    }

    private void migrateSchema(String schema, String targetVersion) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .target(targetVersion)
                .load()
                .migrate();
    }

    private void await(CountDownLatch latch, String failureMessage) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(failureMessage);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

}
