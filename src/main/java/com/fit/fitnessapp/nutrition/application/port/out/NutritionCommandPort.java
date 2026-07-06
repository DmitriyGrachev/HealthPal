package com.fit.fitnessapp.nutrition.application.port.out;

import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySaveResult;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import com.fit.fitnessapp.nutrition.domain.NutritionMonthSaveResult;

import java.util.List;
import java.util.Optional;

public interface NutritionCommandPort {
    void saveToken(Long userId, FatSecretToken token);
    Optional<FatSecretToken> getToken(Long userId);
    List<Long> getAllConnectedUserIds();
    NutritionDaySaveResult saveNutritionDay(NutritionDay nutritionDay);
    NutritionMonthSaveResult saveNutritionMonth(NutritionMonth nutritionMonth);
}
