package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.FatSecretTokenCipher;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.NutritionPersistenceAdapter;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatsecretJpaDay;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatSecretConnectionJpaRepository;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatsecretDayJpaRepository;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatsecretFoodEntryJpaRepository;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionPersistenceAdapterIdempotencyTest {

    @Mock
    private FatSecretConnectionJpaRepository connectionRepository;

    @Mock
    private FatsecretDayJpaRepository dayRepository;

    @Mock
    private FatsecretFoodEntryJpaRepository foodEntryRepository;

    @Mock
    private FatSecretTokenCipher tokenCipher;

    private NutritionPersistenceAdapter adapter;
    private final AtomicReference<FatsecretJpaDay> storedDay = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        adapter = new NutritionPersistenceAdapter(
                connectionRepository,
                dayRepository,
                foodEntryRepository,
                tokenCipher
        );
    }

    @Test
    void dayHashIncludesMacroChanges() {
        stubDaySave();
        LocalDate date = LocalDate.of(2026, 7, 6);
        NutritionDay first = dayWithEntry(42L, date, 1L, 10L, 500, 30.0, 10.0, 50.0);
        NutritionDay changedProtein = dayWithEntry(42L, date, 1L, 10L, 500, 35.0, 10.0, 50.0);

        when(dayRepository.findByUserIdAndDate(42L, date))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.of(storedDay.get()));

        adapter.saveNutritionDay(first);
        clearInvocations(foodEntryRepository);

        adapter.saveNutritionDay(changedProtein);

        verify(foodEntryRepository).deleteByDayId(10L);
    }

    @Test
    void monthSummaryDoesNotChurnAfterEquivalentFullDaySave() {
        stubDaySave();
        LocalDate date = LocalDate.of(2026, 7, 6);
        NutritionDay day = dayWithEntry(42L, date, 1L, 10L, 500, 30.0, 10.0, 50.0);
        when(dayRepository.findByUserIdAndDate(42L, date)).thenReturn(Optional.empty());

        adapter.saveNutritionDay(day);
        FatsecretJpaDay existing = storedDay.get();
        clearInvocations(dayRepository);
        when(dayRepository.findByUserIdAndDateIn(42L, List.of(date))).thenReturn(List.of(existing));

        adapter.saveNutritionMonth(new NutritionMonth(42L, List.of(new NutritionDaySummary(
                42L,
                date,
                (int) date.toEpochDay(),
                500,
                30.0,
                10.0,
                50.0
        ))));

        verify(dayRepository, never()).saveAll(any());
    }

    @Test
    void deletesLocalMonthDaysMissingFromFatSecretSnapshot() {
        LocalDate july1 = LocalDate.of(2026, 7, 1);
        LocalDate july2 = LocalDate.of(2026, 7, 2);
        FatsecretJpaDay deletedDay = storedDay(42L, july1, 500, 30.0, 10.0, 50.0);
        FatsecretJpaDay retainedDay = storedDay(42L, july2, 600, 35.0, 12.0, 55.0);
        when(dayRepository.findByUserIdAndDateBetweenOrderByDate(
                42L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(deletedDay, retainedDay));

        var results = adapter.deleteNutritionDaysMissingFromMonth(
                42L,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                Set.of(july2));

        verify(dayRepository).deleteAll(List.of(deletedDay));
        assertThat(results)
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.userId()).isEqualTo(42L);
                    assertThat(result.date()).isEqualTo(july1);
                    assertThat(result.changed()).isTrue();
                    assertThat(result.totalCalories()).isZero();
                    assertThat(result.protein()).isZero();
                    assertThat(result.fat()).isZero();
                    assertThat(result.carbohydrate()).isZero();
                });
    }

    private NutritionDay dayWithEntry(
            Long userId,
            LocalDate date,
            Long externalFoodId,
            Long externalEntryId,
            int calories,
            double protein,
            double fat,
            double carbs) {
        return new NutritionDay(userId, date, List.of(new FoodEntry(
                externalFoodId,
                externalEntryId,
                "Greek yogurt",
                "Breakfast",
                calories,
                protein,
                fat,
                carbs
        )));
    }

    private FatsecretJpaDay storedDay(
            Long userId,
            LocalDate date,
            double calories,
            double protein,
            double fat,
            double carbohydrate) {
        FatsecretJpaDay day = new FatsecretJpaDay();
        day.setUserId(userId);
        day.setDate(date);
        day.setDateInt((int) date.toEpochDay());
        day.setCalories(calories);
        day.setProtein(protein);
        day.setFat(fat);
        day.setCarbohydrate(carbohydrate);
        day.setSummaryHash("summary-" + date);
        day.setEntriesHash("entries-" + date);
        return day;
    }

    private void stubDaySave() {
        when(dayRepository.save(any(FatsecretJpaDay.class))).thenAnswer(invocation -> {
            FatsecretJpaDay day = invocation.getArgument(0);
            if (day.getId() == null) {
                ReflectionTestUtils.setField(day, "id", 10L);
            }
            storedDay.set(day);
            return day;
        });
    }
}
