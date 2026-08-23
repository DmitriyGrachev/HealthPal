package com.fit.fitnessapp.nutrition.domain;

/** A FatSecret value that the provider policy explicitly permits storing indefinitely. */
public record ProviderDataIdentifier(
        NutritionDataOrigin origin,
        String fieldName,
        String value) {

    public ProviderDataIdentifier {
        if (origin != NutritionDataOrigin.FATSECRET) {
            throw new IllegalArgumentException("provider identifier origin must be FATSECRET");
        }
        ProviderDataRetentionPolicy.validateIdentifier(fieldName, value);
        fieldName = fieldName.trim();
        value = value.trim();
    }
}
