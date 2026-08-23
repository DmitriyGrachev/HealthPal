package com.fit.fitnessapp.nutrition.adapter.out.persistence;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatSecretConnectionJpaEntity;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatSecretConnectionJpaRepository;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.domain.FatSecretConnectionSnapshot;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.NutritionDataOrigin;
import com.fit.fitnessapp.nutrition.domain.ProviderDataIdentifier;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Durable FatSecret boundary: encrypted credentials and explicitly permitted
 * provider identifiers only. Legacy content-write APIs are absent.
 */
@Component
public class NutritionPersistenceAdapter implements NutritionCommandPort {

    private final FatSecretConnectionJpaRepository connectionRepository;
    private final FatSecretTokenCipher tokenCipher;
    private final JdbcTemplate jdbc;

    @Autowired
    public NutritionPersistenceAdapter(
            FatSecretConnectionJpaRepository connectionRepository,
            FatSecretTokenCipher tokenCipher,
            JdbcTemplate jdbc) {
        this.connectionRepository = connectionRepository;
        this.tokenCipher = tokenCipher;
        this.jdbc = jdbc;
    }

    /** Compatibility constructor for narrow unit tests. */
    public NutritionPersistenceAdapter(
            FatSecretConnectionJpaRepository connectionRepository,
            FatSecretTokenCipher tokenCipher) {
        this.connectionRepository = connectionRepository;
        this.tokenCipher = tokenCipher;
        this.jdbc = null;
    }

    @Override
    @Transactional
    public void saveToken(Long userId, FatSecretToken token) {
        lockOwnerIfAvailable(userId);
        FatSecretConnectionJpaEntity entity = connectionRepository.findByUserId(userId)
                .orElseGet(() -> {
                    FatSecretConnectionJpaEntity created = new FatSecretConnectionJpaEntity();
                    created.setUserId(userId);
                    return created;
                });

        if (jdbc != null) {
            jdbc.update("DELETE FROM fatsecret_provider_identifiers WHERE user_id = ?", userId);
        }
        entity.setAccessToken(tokenCipher.encrypt(token.accessToken()));
        entity.setAccessTokenSecret(tokenCipher.encrypt(token.accessTokenSecret()));
        entity.setConnectionEpoch(UUID.randomUUID());
        connectionRepository.saveAndFlush(entity);
    }

    @Override
    public Optional<FatSecretToken> getToken(Long userId) {
        return getConnectionSnapshot(userId).map(FatSecretConnectionSnapshot::token);
    }

    @Override
    public Optional<FatSecretConnectionSnapshot> getConnectionSnapshot(Long userId) {
        return connectionRepository.findByUserId(userId)
                .map(entity -> new FatSecretConnectionSnapshot(
                        entity.getUserId(),
                        new FatSecretToken(
                                tokenCipher.decrypt(entity.getAccessToken()),
                                tokenCipher.decrypt(entity.getAccessTokenSecret())),
                        entity.getConnectionEpoch()));
    }

    @Override
    public List<Long> getAllConnectedUserIds() {
        return connectionRepository.findAll().stream()
                .map(FatSecretConnectionJpaEntity::getUserId)
                .toList();
    }

    @Override
    @Transactional
    public int saveProviderIdentifiers(
            FatSecretConnectionSnapshot connection,
            Set<ProviderDataIdentifier> identifiers) {
        requireJdbc();
        lockOwner(connection.userId());
        int stored = 0;
        for (ProviderDataIdentifier identifier : Set.copyOf(identifiers)) {
            if (identifier.origin() != NutritionDataOrigin.FATSECRET) {
                throw new IllegalArgumentException("only FatSecret identifiers can use this boundary");
            }
            stored += jdbc.update("""
                    INSERT INTO fatsecret_provider_identifiers
                        (user_id, connection_epoch, identifier_type, identifier_value)
                    SELECT ?, ?, ?, ?
                     WHERE EXISTS (
                         SELECT 1
                           FROM fatsecret_connection connection
                          WHERE connection.user_id = ?
                            AND connection.connection_epoch = ?
                     )
                    ON CONFLICT (user_id, connection_epoch, identifier_type, identifier_value)
                    DO UPDATE SET last_received_at = CURRENT_TIMESTAMP
                    """,
                    connection.userId(),
                    connection.connectionEpoch(),
                    identifier.fieldName(),
                    identifier.value(),
                    connection.userId(),
                    connection.connectionEpoch());
        }
        return stored;
    }

    @Override
    public List<ProviderDataIdentifier> getProviderIdentifiers(Long userId) {
        requireJdbc();
        return jdbc.query("""
                SELECT identifier_type, identifier_value
                  FROM fatsecret_provider_identifiers
                 WHERE user_id = ?
                 ORDER BY identifier_type, identifier_value
                """,
                (resultSet, rowNumber) -> new ProviderDataIdentifier(
                        NutritionDataOrigin.FATSECRET,
                        resultSet.getString("identifier_type"),
                        resultSet.getString("identifier_value")),
                userId);
    }

    private void requireJdbc() {
        if (jdbc == null) {
            throw new IllegalStateException("identifier persistence requires JdbcTemplate");
        }
    }

    private void lockOwnerIfAvailable(Long userId) {
        if (jdbc != null) {
            lockOwner(userId);
        }
    }

    private void lockOwner(Long userId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
    }
}
