package com.fit.fitnessapp.nutrition.domain;

import java.util.Objects;

public record NutritionMonthFetchResult(Status status, NutritionMonth snapshot) {

    public enum Status {
        VALID,
        AUTHORITATIVE_EMPTY,
        PROVIDER_ERROR,
        MALFORMED
    }

    public NutritionMonthFetchResult {
        Objects.requireNonNull(status, "status is required");
        if ((status == Status.VALID || status == Status.AUTHORITATIVE_EMPTY) != (snapshot != null)) {
            throw new IllegalArgumentException("A snapshot is required only for authoritative monthly outcomes");
        }
    }

    public static NutritionMonthFetchResult valid(NutritionMonth snapshot) {
        return new NutritionMonthFetchResult(Status.VALID, snapshot);
    }

    public static NutritionMonthFetchResult authoritativeEmpty(NutritionMonth snapshot) {
        return new NutritionMonthFetchResult(Status.AUTHORITATIVE_EMPTY, snapshot);
    }

    public static NutritionMonthFetchResult providerError() {
        return new NutritionMonthFetchResult(Status.PROVIDER_ERROR, null);
    }

    public static NutritionMonthFetchResult malformed() {
        return new NutritionMonthFetchResult(Status.MALFORMED, null);
    }
}
