package com.fit.fitnessapp.nutrition.adapter.out.persistence;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatSecretConnectionJpaEntity;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatsecretFoodEntry;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatsecretJpaDay;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatSecretConnectionJpaRepository;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatsecretDayJpaRepository;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionPersistencePort;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NutritionPersistenceAdapter implements NutritionPersistencePort {

    private final FatSecretConnectionJpaRepository connectionRepository;
    private final FatsecretDayJpaRepository dayRepository;
    // private final NutritionDayJpaRepository dayRepository; // Добавишь позже для дней

    @Override
    public void saveToken(Long userId, FatSecretToken token) {
        FatSecretConnectionJpaEntity entity = connectionRepository.findByUserId(userId)
                .orElseGet(() -> {
                    FatSecretConnectionJpaEntity newEntity = new FatSecretConnectionJpaEntity();
                    newEntity.setUserId(userId);
                    return newEntity;
                });

        entity.setAccessToken(token.accessToken());
        entity.setAccessTokenSecret(token.accessTokenSecret());

        connectionRepository.save(entity);
    }

    @Override
    public Optional<FatSecretToken> getToken(Long userId) {
        return connectionRepository.findByUserId(userId)
                .map(entity -> new FatSecretToken(entity.getAccessToken(), entity.getAccessTokenSecret()));
    }

    @Override
    public void saveNutritionDay(NutritionDay nutritionDay) {

    }

    @Override
    public void saveNutritionMonth(NutritionMonth nutritionMonth) { // [cite: 5]

    }
}