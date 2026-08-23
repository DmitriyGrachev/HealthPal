package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.NutritionPersistenceAdapter;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.FatSecretTokenCipher;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatSecretConnectionJpaEntity;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatSecretConnectionJpaRepository;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionPersistenceAdapterTokenEncryptionTest {

    @Mock
    private FatSecretConnectionJpaRepository connectionRepository;

    @Test
    void saveTokenStoresEncryptedColumnsAndGetTokenReturnsPlaintextToken() {
        NutritionPersistenceAdapter adapter = new NutritionPersistenceAdapter(
                connectionRepository,
                new FatSecretTokenCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", new MockEnvironment())
        );
        Long userId = 42L;
        FatSecretToken token = new FatSecretToken("access-token", "access-secret");
        when(connectionRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(connectionRepository.saveAndFlush(any(FatSecretConnectionJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        adapter.saveToken(userId, token);

        ArgumentCaptor<FatSecretConnectionJpaEntity> entityCaptor =
                ArgumentCaptor.forClass(FatSecretConnectionJpaEntity.class);
        verify(connectionRepository).saveAndFlush(entityCaptor.capture());
        FatSecretConnectionJpaEntity storedEntity = entityCaptor.getValue();
        assertThat(storedEntity.getAccessToken()).isNotEqualTo(token.accessToken());
        assertThat(storedEntity.getAccessToken()).startsWith("enc:v1:");
        assertThat(storedEntity.getAccessTokenSecret()).isNotEqualTo(token.accessTokenSecret());
        assertThat(storedEntity.getAccessTokenSecret()).startsWith("enc:v1:");
        assertThat(storedEntity.getConnectionEpoch()).isNotNull();

        when(connectionRepository.findByUserId(userId)).thenReturn(Optional.of(storedEntity));
        assertThat(adapter.getToken(userId)).contains(token);
    }
}
