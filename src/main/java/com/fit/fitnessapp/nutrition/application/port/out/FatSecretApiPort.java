package com.fit.fitnessapp.nutrition.application.port.out;

import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import com.fit.fitnessapp.nutrition.domain.WeightEntryDto;
import com.fit.fitnessapp.nutrition.domain.FatSecretExerciseDto;
import com.fit.fitnessapp.nutrition.domain.FatSecretExerciseEntryDto;
import java.util.List;

public interface FatSecretApiPort {
    String getAuthUrl(Long userId);
    FatSecretAuthResult exchangeToken(String oauthToken, String oauthVerifier);

    NutritionDay fetchAndParseFoodEntries(FatSecretToken token, Long userId, long daysSinceEpoch);
    NutritionMonth fetchAndParseFoodEntriesForCurrentMonth(FatSecretToken token, Long userId, long currentDaysInMonth);
    
    // Phase 1 additions
    WeightEntryDto getLatestWeight(FatSecretToken token);
    List<WeightEntryDto> getWeightHistory(FatSecretToken token, long daysSinceEpoch);
    boolean updateWeight(FatSecretToken token, WeightEntryDto weightEntry);

    // Exercise additions
    List<FatSecretExerciseDto> getExercises(FatSecretToken token);
    List<FatSecretExerciseEntryDto> getExerciseEntries(FatSecretToken token, long daysSinceEpoch);
}