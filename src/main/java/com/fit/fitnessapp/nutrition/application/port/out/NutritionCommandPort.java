package com.fit.fitnessapp.nutrition.application.port.out;

import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySaveResult;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import com.fit.fitnessapp.nutrition.domain.NutritionMonthSaveResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface NutritionCommandPort {
    void saveToken(Long userId, FatSecretToken token);
    Optional<FatSecretToken> getToken(Long userId);
    List<Long> getAllConnectedUserIds();
    NutritionDaySaveResult saveNutritionDay(NutritionDay nutritionDay);
    NutritionMonthSaveResult saveNutritionMonth(NutritionMonth nutritionMonth);
    List<NutritionDaySaveResult> deleteNutritionDaysMissingFromMonth(
            Long userId,
            LocalDate monthStart,
            LocalDate monthEnd,
            Set<LocalDate> presentDates);
}
