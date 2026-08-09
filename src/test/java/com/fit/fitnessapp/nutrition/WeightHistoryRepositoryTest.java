package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.WeightHistoryJpaRepository;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.WeightHistoryRepository;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.WeightHistory;
import com.fit.fitnessapp.nutrition.domain.WeightHistoryDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeightHistoryRepositoryTest {

    @Mock
    private WeightHistoryJpaRepository jpaRepository;

    @Test
    void saveWeightUpdatesExistingUserDateSourceRow() {
        WeightHistoryRepository repository = new WeightHistoryRepository(jpaRepository);
        LocalDate date = LocalDate.of(2026, 7, 5);
        WeightHistory existing = new WeightHistory(
                10L,
                42L,
                new BigDecimal("82.00"),
                date,
                WeightHistory.WeightSource.FATSECRET,
                null
        );
        when(jpaRepository.findByUserIdAndDateAndSource(42L, date, WeightHistory.WeightSource.FATSECRET))
                .thenReturn(Optional.of(existing));
        when(jpaRepository.save(existing)).thenReturn(existing);

        WeightHistoryDto saved = repository.saveWeight(new WeightHistoryDto(
                null,
                42L,
                new BigDecimal("81.50"),
                date,
                WeightHistoryDto.WeightSource.FATSECRET));

        verify(jpaRepository).save(existing);
        assertThat(existing.getWeightKg()).isEqualByComparingTo("81.50");
        assertThat(saved.id()).isEqualTo(10L);
        assertThat(saved.weightKg()).isEqualByComparingTo("81.50");
    }
}
