package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.domain.NutritionDataOrigin;
import com.fit.fitnessapp.nutrition.domain.ProviderDataIdentifier;
import com.fit.fitnessapp.nutrition.domain.ProviderDataRetentionPolicy;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FatSecretProviderDataRetentionPolicyTest {

    @Test
    void exposesTheExactFatSecretIndefiniteStorageAllowlist() {
        assertThat(ProviderDataRetentionPolicy.permittedFields()).containsExactlyInAnyOrderElementsOf(Set.of(
                "auth_secret",
                "auth_token",
                "exercise_id",
                "food_category_id",
                "food_entry_id",
                "food_id",
                "recipe_id",
                "recipe_types",
                "saved_meal_id",
                "saved_meal_item_id",
                "serving_id"));
    }

    @Test
    void isolatesAllowedProviderIdentifiersFromCredentialsAndRestrictedContent() {
        ProviderDataIdentifier identifier = ProviderDataRetentionPolicy.identifier("food_id", "12345");

        assertThat(identifier.origin()).isEqualTo(NutritionDataOrigin.FATSECRET);
        assertThat(identifier.fieldName()).isEqualTo("food_id");
        assertThat(identifier.value()).isEqualTo("12345");
        assertThat(ProviderDataRetentionPolicy.permittedIdentifierFields())
                .doesNotContain("auth_token", "auth_secret");

        assertThatThrownBy(() -> ProviderDataRetentionPolicy.identifier("food_entry_name", "private meal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not permitted");
        assertThatThrownBy(() -> ProviderDataRetentionPolicy.identifier("food_id", "private meal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shape");
        assertThatThrownBy(() -> ProviderDataRetentionPolicy.identifier("auth_token", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credential");
    }

    @Test
    void acceptsOnlyBoundedRecipeTypeIdentifiers() {
        assertThat(ProviderDataRetentionPolicy.identifier("recipe_types", "breakfast,high-protein").value())
                .isEqualTo("breakfast,high-protein");

        assertThatThrownBy(() -> ProviderDataRetentionPolicy.identifier("recipe_types", "breakfast|private name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shape");
    }
}
