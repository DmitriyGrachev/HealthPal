package com.fit.fitnessapp.analytics.application;

import com.fit.fitnessapp.analytics.MonthlyReportRequestedEvent;
import com.fit.fitnessapp.nutrition.NutritionMonthlyApi;
import com.fit.fitnessapp.nutrition.NutritionMonthlyStatsDto;
import com.fit.fitnessapp.workout.WorkoutMonthlyApi;
import com.fit.fitnessapp.workout.WorkoutMonthlyStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyReportTransactionService {

    private final NutritionMonthlyApi nutritionApi;
    private final WorkoutMonthlyApi workoutApi;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateForUser(Long userId, LocalDate monthStart, LocalDate monthEnd) {
        log.info("Сбор месячных данных для юзера {} за период {} - {}", userId, monthStart, monthEnd);

        NutritionMonthlyStatsDto nDto = nutritionApi.getMonthlyStats(userId, monthStart, monthEnd);
        WorkoutMonthlyStatsDto wDto = workoutApi.getMonthlyStats(userId, monthStart, monthEnd);

        MonthlyReportRequestedEvent event = buildEvent(userId, monthStart, monthEnd, nDto, wDto);
        eventPublisher.publishEvent(event);
    }

    private MonthlyReportRequestedEvent buildEvent(Long userId, LocalDate start, LocalDate end,
                                                   NutritionMonthlyStatsDto nDto,
                                                   WorkoutMonthlyStatsDto wDto) {

        Map<String, MonthlyReportRequestedEvent.DailyMacrosSnapshot> dailyBreakdown =
                nDto.getDailyBreakdown().entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> new MonthlyReportRequestedEvent.DailyMacrosSnapshot(
                                        e.getValue().getCalories(), e.getValue().getProtein(),
                                        e.getValue().getFat(), e.getValue().getCarbs()
                                )
                        ));

        MonthlyReportRequestedEvent.NutritionSnapshot nutrition = new MonthlyReportRequestedEvent.NutritionSnapshot(
                nDto.getTotalCalories(), nDto.getAvgCalories(), nDto.getAvgProtein(),
                nDto.getAvgFat(), nDto.getAvgCarbs(), nDto.getDaysTracked(), dailyBreakdown
        );

        MonthlyReportRequestedEvent.WorkoutSnapshot workout = new MonthlyReportRequestedEvent.WorkoutSnapshot(
                wDto.getTotalSessions(), wDto.getTotalVolumeKg(),
                wDto.getAvgVolumePerSession(), wDto.getVolumeByDay()
        );

        return new MonthlyReportRequestedEvent(userId, start, end, nutrition, workout);
    }
}