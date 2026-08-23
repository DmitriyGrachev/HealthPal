package com.fit.fitnessapp.nutrition.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fixed FatSecret indefinite-storage allowlist.
 *
 * <p>Everything not named here, including hashes and derived representations,
 * remains restricted provider content and must not cross a durable boundary.</p>
 */
public final class ProviderDataRetentionPolicy {

    private static final Set<String> CREDENTIAL_FIELDS = Set.of("auth_secret", "auth_token");
    private static final Set<String> IDENTIFIER_FIELDS = Set.of(
            "exercise_id",
            "food_category_id",
            "food_entry_id",
            "food_id",
            "recipe_id",
            "recipe_types",
            "saved_meal_id",
            "saved_meal_item_id",
            "serving_id");
    private static final Set<String> PERMITTED_FIELDS;
    private static final Pattern NUMERIC_IDENTIFIER = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern RECIPE_TYPES = Pattern.compile(
            "[A-Za-z0-9_-]+(?:,[A-Za-z0-9_-]+)*");

    static {
        LinkedHashSet<String> fields = new LinkedHashSet<>(CREDENTIAL_FIELDS);
        fields.addAll(IDENTIFIER_FIELDS);
        PERMITTED_FIELDS = Collections.unmodifiableSet(fields);
    }

    private ProviderDataRetentionPolicy() {
    }

    public static Set<String> permittedFields() {
        return PERMITTED_FIELDS;
    }

    public static Set<String> permittedIdentifierFields() {
        return IDENTIFIER_FIELDS;
    }

    public static ProviderDataIdentifier identifier(String fieldName, String value) {
        if (fieldName != null && CREDENTIAL_FIELDS.contains(fieldName.trim())) {
            throw new IllegalArgumentException("credentials must use the encrypted connection boundary");
        }
        return new ProviderDataIdentifier(NutritionDataOrigin.FATSECRET, fieldName, value);
    }

    static void validateIdentifier(String fieldName, String value) {
        if (fieldName == null || !IDENTIFIER_FIELDS.contains(fieldName.trim())) {
            throw new IllegalArgumentException("provider field is not permitted as a durable identifier");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("provider identifier shape is invalid");
        }
        String normalizedField = fieldName.trim();
        String normalizedValue = value.trim();
        Pattern shape = "recipe_types".equals(normalizedField) ? RECIPE_TYPES : NUMERIC_IDENTIFIER;
        if (normalizedValue.length() > 128 || !shape.matcher(normalizedValue).matches()) {
            throw new IllegalArgumentException("provider identifier shape is invalid");
        }
    }
}
