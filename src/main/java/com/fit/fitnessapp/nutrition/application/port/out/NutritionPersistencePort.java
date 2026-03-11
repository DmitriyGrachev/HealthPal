package com.fit.fitnessapp.nutrition.application.port.out;

import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;

import java.util.Optional;

public interface NutritionPersistencePort {
    void saveToken(Long userId, FatSecretToken token);
    Optional<FatSecretToken> getToken(Long userId);
    void saveNutritionDay(NutritionDay nutritionDay);
    void saveNutritionMonth(NutritionMonth nutritionMonth); // ✅

}
