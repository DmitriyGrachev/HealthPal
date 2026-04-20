package com.fit.fitnessapp.ai.application.port.out;

import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;

public interface AiModelPort {
    //Default
    NutritionInsightResponse generate(String prompt);

    NutritionInsightResponse generate(String prompt, String modelName);
}