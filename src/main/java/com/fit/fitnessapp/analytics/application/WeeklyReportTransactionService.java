package com.fit.fitnessapp.analytics.application;

import com.fit.fitnessapp.api.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.nutrition.NutritionWeeklyApi;
import com.fit.fitnessapp.nutrition.NutritionWeeklyStatsDto;
import com.fit.fitnessapp.workout.WorkoutWeeklyApi;
import com.fit.fitnessapp.workout.WorkoutWeeklyStatsDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeeklyReportTransactionService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportTransactionService.class);

    private final NutritionWeeklyApi nutritionApi;
    private final WorkoutWeeklyApi workoutApi;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateForUser(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        log.info("Collecting weekly report data userId={} periodStart={} periodEnd={}",
                userId, weekStart, weekEnd);

        NutritionWeeklyStatsDto nDto = nutritionApi.getWeeklyStats(userId, weekStart, weekEnd);
        WorkoutWeeklyStatsDto wDto = workoutApi.getWeeklyStats(userId, weekStart, weekEnd);

        eventPublisher.publishEvent(buildEvent(userId, weekStart, weekEnd, nDto, wDto));
    }

    private WeeklyReportRequestedEvent buildEvent(
            Long userId,
            LocalDate start,
            LocalDate end,
            NutritionWeeklyStatsDto nDto,
            WorkoutWeeklyStatsDto wDto) {

        Map<String, WeeklyReportRequestedEvent.DailyMacrosSnapshot> dailyBreakdown =
                nDto.getDailyBreakdown().entrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> entry.getKey(),
                                entry -> new WeeklyReportRequestedEvent.DailyMacrosSnapshot(
                                        entry.getValue().getCalories(),
                                        entry.getValue().getProtein(),
                                        entry.getValue().getFat(),
                                        entry.getValue().getCarbs())));

        WeeklyReportRequestedEvent.NutritionSnapshot nutrition = new WeeklyReportRequestedEvent.NutritionSnapshot(
                nDto.getTotalCalories(),
                nDto.getAvgCalories(),
                nDto.getAvgProtein(),
                nDto.getAvgFat(),
                nDto.getAvgCarbs(),
                dailyBreakdown);

        WeeklyReportRequestedEvent.WorkoutSnapshot workout = new WeeklyReportRequestedEvent.WorkoutSnapshot(
                wDto.getTotalSessions(),
                wDto.getTotalVolumeKg(),
                wDto.getCardioSessions(),
                wDto.getCardioDurationSeconds(),
                wDto.getCardioCalories(),
                wDto.getVolumeByDay());

        return new WeeklyReportRequestedEvent(userId, start, end, nutrition, workout);
    }
}
