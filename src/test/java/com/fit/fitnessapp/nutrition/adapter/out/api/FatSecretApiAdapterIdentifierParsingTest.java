package com.fit.fitnessapp.nutrition.adapter.out.api;

import tools.jackson.databind.ObjectMapper;
import com.fit.fitnessapp.nutrition.domain.ProviderDataIdentifier;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FatSecretApiAdapterIdentifierParsingTest {

    @SuppressWarnings("unchecked")
    private final FatSecretApiAdapter adapter = new FatSecretApiAdapter(
            mock(Cache.class),
            new ObjectMapper());

    @Test
    void mapsOnlyExplicitlyStorableFoodEntryIdentifiers() {
        Set<ProviderDataIdentifier> identifiers = adapter.parseProviderIdentifiers("""
                {
                  "food_entries": {
                    "food_entry": [{
                      "food_id": "101",
                      "food_entry_id": "202",
                      "serving_id": "303",
                      "food_entry_name": "private meal canary",
                      "meal": "private label canary",
                      "calories": "450",
                      "protein": "30.0"
                    }]
                  }
                }
                """);

        assertThat(identifiers)
                .extracting(ProviderDataIdentifier::fieldName)
                .containsExactlyInAnyOrder("food_id", "food_entry_id", "serving_id");
        assertThat(identifiers)
                .extracting(ProviderDataIdentifier::value)
                .containsExactlyInAnyOrder("101", "202", "303")
                .noneMatch(value -> value.contains("private"));
    }

    @Test
    void supportsTheProviderSingleObjectShapeAndAnEmptyResponse() {
        assertThat(adapter.parseProviderIdentifiers("""
                {"food_entries":{"food_entry":{"food_id":"101","food_entry_id":"202"}}}
                """))
                .extracting(ProviderDataIdentifier::fieldName)
                .containsExactlyInAnyOrder("food_id", "food_entry_id");

        assertThat(adapter.parseProviderIdentifiers("{\"food_entries\":{}}"))
                .isEmpty();
    }
}
