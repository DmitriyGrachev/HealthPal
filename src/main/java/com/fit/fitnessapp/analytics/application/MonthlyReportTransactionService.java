package com.fit.fitnessapp.analytics.application;

import com.fit.fitnessapp.api.MonthlyReportRequestedEvent;
import com.fit.fitnessapp.nutrition.NutritionMonthlyApi;
import com.fit.fitnessapp.nutrition.NutritionMonthlyStatsDto;
import com.fit.fitnessapp.workout.WorkoutMonthlyApi;
import com.fit.fitnessapp.workout.WorkoutMonthlyStatsDto;
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
public class MonthlyReportTransactionService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportTransactionService.class);

    private final NutritionMonthlyApi nutritionApi;
    private final WorkoutMonthlyApi workoutApi;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateForUser(Long userId, LocalDate monthStart, LocalDate monthEnd) {
        log.info("Collecting monthly report data userId={} periodStart={} periodEnd={}",
                userId, monthStart, monthEnd);

        NutritionMonthlyStatsDto nDto = nutritionApi.getMonthlyStats(userId, monthStart, monthEnd);
        WorkoutMonthlyStatsDto wDto = workoutApi.getMonthlyStats(userId, monthStart, monthEnd);

        eventPublisher.publishEvent(buildEvent(userId, monthStart, monthEnd, nDto, wDto));
    }

    private MonthlyReportRequestedEvent buildEvent(
            Long userId,
            LocalDate start,
            LocalDate end,
            NutritionMonthlyStatsDto nDto,
            WorkoutMonthlyStatsDto wDto) {

        Map<String, MonthlyReportRequestedEvent.DailyMacrosSnapshot> dailyBreakdown =
                nDto.getDailyBreakdown().entrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> entry.getKey(),
                                entry -> new MonthlyReportRequestedEvent.DailyMacrosSnapshot(
                                        entry.getValue().getCalories(),
                                        entry.getValue().getProtein(),
                                        entry.getValue().getFat(),
                                        entry.getValue().getCarbs())));

        MonthlyReportRequestedEvent.NutritionSnapshot nutrition = new MonthlyReportRequestedEvent.NutritionSnapshot(
                nDto.getTotalCalories(),
                nDto.getAvgCalories(),
                nDto.getAvgProtein(),
                nDto.getAvgFat(),
                nDto.getAvgCarbs(),
                nDto.getDaysTracked(),
                dailyBreakdown);

        MonthlyReportRequestedEvent.WorkoutSnapshot workout = new MonthlyReportRequestedEvent.WorkoutSnapshot(
                wDto.getTotalSessions(),
                wDto.getTotalVolumeKg(),
                wDto.getAvgVolumePerSession(),
                wDto.getCardioSessions(),
                wDto.getCardioDurationSeconds(),
                wDto.getCardioCalories(),
                wDto.getVolumeByDay());

        return new MonthlyReportRequestedEvent(userId, start, end, nutrition, workout);
    }
}
