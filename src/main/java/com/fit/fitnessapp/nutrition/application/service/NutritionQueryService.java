package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionQueryPort;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class NutritionQueryService implements NutritionQueryUseCase {

    private final NutritionQueryPort queryPort; // ← Spring JDBC адаптер

    @Override
    public NutritionDay getDay(Long userId, LocalDate date) {
        return queryPort.getDayByDate(userId, date)
                .orElseThrow(() -> new NoSuchElementException(
                        "No data for " + date + ". Sync first."));
    }

    @Override
    public List<NutritionDaySummary> getCurrentMonthSummary(Long userId) {
        return queryPort.getSummaryByDateRange(
                userId,
                LocalDate.now().withDayOfMonth(1),
                LocalDate.now()
        );
    }

    @Override
    public List<NutritionDaySummary> getDateRange(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new IllegalArgumentException("'from' must be before 'to'");
        if (from.plusDays(90).isBefore(to)) throw new IllegalArgumentException("Max range: 90 days");
        return queryPort.getSummaryByDateRange(userId, from, to);
    }
}