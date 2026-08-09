package com.fit.fitnessapp.nutrition.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NutritionDayStatusTest {

    @Test
    @DisplayName("Should distinguish PRESENT, NO_RECORDS, and MISSING_SYNC")
    void status_DistinguishesMissingFromZero() {
        NutritionDay present = new NutritionDay(
                1L, LocalDate.now(), List.of(new FoodEntry(1L, 10L, "Oats", "Breakfast", 300, 10.0, 5.0, 50.0))
        );
        assertThat(present.status()).isEqualTo(NutritionDataStatus.PRESENT);
        assertThat(present.hasNutritionData()).isTrue();

        NutritionDay noRecords = new NutritionDay(1L, LocalDate.now(), List.of());
        assertThat(noRecords.status()).isEqualTo(NutritionDataStatus.NO_RECORDS);
        assertThat(noRecords.hasNutritionData()).isFalse();

        NutritionDay missingSync = NutritionDay.missingSync(1L, LocalDate.now());
        assertThat(missingSync.status()).isEqualTo(NutritionDataStatus.MISSING_SYNC);
        assertThat(missingSync.hasNutritionData()).isFalse();
    }
}
