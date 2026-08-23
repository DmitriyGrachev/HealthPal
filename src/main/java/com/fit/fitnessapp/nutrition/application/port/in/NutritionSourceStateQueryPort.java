package com.fit.fitnessapp.nutrition.application.port.in;

import com.fit.fitnessapp.api.DomainSourceState;

import java.time.LocalDate;
import java.util.Optional;

/** Narrow read boundary for current nutrition source truth. */
public interface NutritionSourceStateQueryPort {

    Optional<DomainSourceState> findCurrent(Long userId, LocalDate sourceDate);
}
