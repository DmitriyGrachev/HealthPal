package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.adapter.out.persistence.repository.AuthFatSecretConnectionJpaRepository;
import com.fit.fitnessapp.auth.FatSecretTokenUseCase;
import com.fit.fitnessapp.auth.adapter.out.persistence.entity.AuthFatSecretConnectionJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FatSecretTokenService implements FatSecretTokenUseCase {

    private final AuthFatSecretConnectionJpaRepository repository;

    @Override
    @Transactional
    public void saveToken(Long userId, String accessToken, String accessTokenSecret) {
        AuthFatSecretConnectionJpaEntity entity = repository.findByUserId(userId)
                .orElse(new AuthFatSecretConnectionJpaEntity());
        entity.setUserId(userId);
        entity.setAccessToken(accessToken);
        entity.setAccessTokenSecret(accessTokenSecret);
        repository.save(entity);
    }

    @Override
    public Optional<FatSecretTokenData> getToken(Long userId) {
        return repository.findByUserId(userId)
                .map(entity -> new FatSecretTokenData(entity.getAccessToken(), entity.getAccessTokenSecret()));
    }
}
