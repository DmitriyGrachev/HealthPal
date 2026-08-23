package com.fit.fitnessapp.nutrition.application.port.out;

import com.fit.fitnessapp.api.ChangeType;
import com.fit.fitnessapp.api.DomainSourceState;

import java.time.LocalDate;
import java.util.Optional;

/** Internal PostgreSQL boundary for versioning nutrition source state. */
public interface NutritionSourceStatePort {

    Optional<DomainSourceState> advance(
            Long userId,
            LocalDate sourceDate,
            ChangeType changeType,
            String contentHash);

}
