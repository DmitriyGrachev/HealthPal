package com.fit.fitnessapp.nutrition.application.port.out;

import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;

public interface FatSecretApiPort {
    String getAuthUrl(Long userId);
    FatSecretAuthResult exchangeToken(String oauthToken, String oauthVerifier);

    NutritionDay fetchAndParseFoodEntries(FatSecretToken token, Long userId, long daysSinceEpoch);
    //NutritionDay fetchAndParseFoodEntriesForCurrentMonth(FatSecretToken token, Long userId, long currentDaysInMonth );
    NutritionMonth fetchAndParseFoodEntriesForCurrentMonth(FatSecretToken token, Long userId, long currentDaysInMonth); // ✅

}