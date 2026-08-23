package com.fit.fitnessapp.experiment.domain;

/** Optional manual context rating. A missing rating is represented by a null field on its container. */
public record ContextRating(Integer value) {
    public ContextRating {
        if (value == null || value < 0 || value > 10) {
            throw new IllegalArgumentException("context rating must be between 0 and 10");
        }
    }
}
