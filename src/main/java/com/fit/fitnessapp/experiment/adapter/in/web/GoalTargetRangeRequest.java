package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.TargetRange;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record GoalTargetRangeRequest(
        @DecimalMin(value = "-1000000000", inclusive = true) @DecimalMax(value = "1000000000", inclusive = true)
        Double minimum,
        @DecimalMin(value = "-1000000000", inclusive = true) @DecimalMax(value = "1000000000", inclusive = true)
        Double maximum,
        @Size(max = 32) String unit) {
    @AssertTrue(message = "target range minimum must not exceed maximum")
    public boolean isOrdered() {
        return minimum == null || maximum == null || minimum <= maximum;
    }

    @AssertTrue(message = "target range must contain a bound and unit")
    public boolean isSpecified() {
        return (minimum != null || maximum != null) && unit != null && !unit.isBlank();
    }

    public TargetRange toDomain() {
        if (minimum == null && maximum == null && (unit == null || unit.isBlank())) {
            return null;
        }
        return new TargetRange(minimum, maximum, unit);
    }
}
