package com.fit.fitnessapp.experiment.domain;

public record TargetRange(Double minimum, Double maximum, String unit) {
    public TargetRange {
        if (minimum == null && maximum == null) {
            throw new IllegalArgumentException("target range must contain a bound");
        }
        if (minimum != null && maximum != null && minimum > maximum) {
            throw new IllegalArgumentException("target range minimum must not exceed maximum");
        }
        if (minimum != null && !Double.isFinite(minimum)
                || maximum != null && !Double.isFinite(maximum)) {
            throw new IllegalArgumentException("target range bounds must be finite");
        }
        if (unit == null || unit.isBlank() || unit.length() > 32) {
            throw new IllegalArgumentException("target range unit must be between 1 and 32 characters");
        }
        unit = unit.trim();
    }
}
