package com.fit.fitnessapp.nutrition.application.port.in;

import java.time.LocalDate;

public interface SyncNutritionUseCase {
    void syncDay(Long userId, LocalDate date);
    void syncMonth(Long userId);

}
