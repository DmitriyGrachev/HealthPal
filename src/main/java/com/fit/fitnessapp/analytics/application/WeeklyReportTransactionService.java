package com.fit.fitnessapp.analytics.application;

import com.fit.fitnessapp.analytics.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.auth.UserApi;
import com.fit.fitnessapp.nutrition.NutritionWeeklyApi;
import com.fit.fitnessapp.nutrition.NutritionWeeklyStatsDto;
import com.fit.fitnessapp.workout.WorkoutWeeklyApi;
import com.fit.fitnessapp.workout.WorkoutWeeklyStatsDto;
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
public class WeeklyReportTransactionService {

    private final NutritionWeeklyApi nutritionApi;
    private final WorkoutWeeklyApi workoutApi;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateForUser(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        log.info("Сбор данных для юзера {} за период {} - {}", userId, weekStart, weekEnd);

        NutritionWeeklyStatsDto nDto = nutritionApi.getWeeklyStats(userId, weekStart, weekEnd);
        WorkoutWeeklyStatsDto wDto = workoutApi.getWeeklyStats(userId, weekStart, weekEnd);

        WeeklyReportRequestedEvent event = buildEvent(userId, weekStart, weekEnd, nDto, wDto);
        eventPublisher.publishEvent(event); // Летит в БД Outbox
    }

    private WeeklyReportRequestedEvent buildEvent(Long userId, LocalDate start, LocalDate end,
                                                  NutritionWeeklyStatsDto nDto, WorkoutWeeklyStatsDto wDto) {

        Map<String, WeeklyReportRequestedEvent.DailyMacrosSnapshot> dailyBreakdown = nDto.getDailyBreakdown().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new WeeklyReportRequestedEvent.DailyMacrosSnapshot(
                                e.getValue().getCalories(), e.getValue().getProtein(),
                                e.getValue().getFat(), e.getValue().getCarbs()
                        )
                ));

        WeeklyReportRequestedEvent.NutritionSnapshot nutrition = new WeeklyReportRequestedEvent.NutritionSnapshot(
                nDto.getTotalCalories(), nDto.getAvgCalories(), nDto.getAvgProtein(),
                nDto.getAvgFat(), nDto.getAvgCarbs(), dailyBreakdown
        );

        WeeklyReportRequestedEvent.WorkoutSnapshot workout = new WeeklyReportRequestedEvent.WorkoutSnapshot(
                wDto.getTotalSessions(), wDto.getTotalVolumeKg(), wDto.getVolumeByDay()
        );

        return new WeeklyReportRequestedEvent(userId, start, end, nutrition, workout);
    }
}
