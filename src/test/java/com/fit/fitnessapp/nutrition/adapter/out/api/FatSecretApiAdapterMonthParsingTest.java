package com.fit.fitnessapp.nutrition.adapter.out.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit.fitnessapp.nutrition.domain.NutritionMonthFetchResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FatSecretApiAdapterMonthParsingTest {

    private final FatSecretApiAdapter adapter = new FatSecretApiAdapter(mock(), new ObjectMapper());

    @Test
    void parsesArrayOfDaysAsValid() {
        NutritionMonthFetchResult result = adapter.parseMonthResponse("""
                {"month":{"day":[
                  {"date_int":"20635","calories":"500","protein":"30","fat":"10","carbohydrate":"50"},
                  {"date_int":"20636","calories":"600","protein":"40","fat":"20","carbohydrate":"60"}
                ]}}
                """, 42L);

        assertThat(result.status()).isEqualTo(NutritionMonthFetchResult.Status.VALID);
        assertThat(result.snapshot().days()).hasSize(2);
    }

    @Test
    void parsesSingletonDayAsValid() {
        NutritionMonthFetchResult result = adapter.parseMonthResponse("""
                {"month":{"day":{"date_int":"20635","calories":"500","protein":"30","fat":"10","carbohydrate":"50"}}}
                """, 42L);

        assertThat(result.status()).isEqualTo(NutritionMonthFetchResult.Status.VALID);
        assertThat(result.snapshot().days()).singleElement().satisfies(day -> assertThat(day.dateInt()).isEqualTo(20635));
    }

    @Test
    void classifiesExplicitEmptyDaysAsAuthoritativeEmpty() {
        NutritionMonthFetchResult result = adapter.parseMonthResponse("{\"month\":{\"day\":[]}}", 42L);

        assertThat(result.status()).isEqualTo(NutritionMonthFetchResult.Status.AUTHORITATIVE_EMPTY);
        assertThat(result.snapshot().days()).isEmpty();
    }

    @Test
    void classifiesProviderError() {
        assertThat(adapter.parseMonthResponse("{\"error\":{\"code\":2}}", 42L).status())
                .isEqualTo(NutritionMonthFetchResult.Status.PROVIDER_ERROR);
    }

    @Test
    void classifiesNonObjectErrorPayloadsAsMalformed() {
        assertThat(adapter.parseMonthResponse("{\"error\":null}", 42L).status())
                .isEqualTo(NutritionMonthFetchResult.Status.MALFORMED);
        assertThat(adapter.parseMonthResponse("{\"error\":\"unexpected\"}", 42L).status())
                .isEqualTo(NutritionMonthFetchResult.Status.MALFORMED);
        assertThat(adapter.parseMonthResponse("{\"error\":[]}", 42L).status())
                .isEqualTo(NutritionMonthFetchResult.Status.MALFORMED);
    }

    @Test
    void classifiesMalformedJson() {
        assertThat(adapter.parseMonthResponse("{", 42L).status())
                .isEqualTo(NutritionMonthFetchResult.Status.MALFORMED);
    }

    @Test
    void classifiesMissingMonthStructureAsMalformed() {
        assertThat(adapter.parseMonthResponse("{}", 42L).status())
                .isEqualTo(NutritionMonthFetchResult.Status.MALFORMED);
    }

    @Test
    void classifiesWrongDayTypeAsMalformed() {
        assertThat(adapter.parseMonthResponse("{\"month\":{\"day\":\"unexpected\"}}", 42L).status())
                .isEqualTo(NutritionMonthFetchResult.Status.MALFORMED);
    }

    @Test
    void classifiesInvalidRequiredDayValueAsMalformed() {
        assertThat(adapter.parseMonthResponse("""
                {"month":{"day":{"date_int":"bad","calories":"500","protein":"30","fat":"10","carbohydrate":"50"}}}
                """, 42L).status()).isEqualTo(NutritionMonthFetchResult.Status.MALFORMED);
    }
}
