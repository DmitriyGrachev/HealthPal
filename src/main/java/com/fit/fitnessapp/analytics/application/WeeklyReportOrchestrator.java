package com.fit.fitnessapp.analytics.application;

import com.fit.fitnessapp.analytics.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.auth.UserApi;
import com.fit.fitnessapp.nutrition.NutritionWeeklyApi;
import com.fit.fitnessapp.nutrition.NutritionWeeklyStatsDto;
import com.fit.fitnessapp.workout.WorkoutWeeklyApi;
import com.fit.fitnessapp.workout.WorkoutWeeklyStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportOrchestrator {

    private final NutritionWeeklyApi nutritionApi;
    private final WorkoutWeeklyApi workoutApi;
    private final UserApi userApi;
    private final ApplicationEventPublisher eventPublisher;

    @Lazy
    @Autowired
    private WeeklyReportOrchestrator self;

    // Запускаем каждый понедельник в 09:00
    @Scheduled(cron = "0 0 9 * * MON")
    public void generateWeeklyReports() {
        log.info("Запуск генерации еженедельных отчетов...");

        LocalDate weekEnd = LocalDate.now().minusDays(1); // Воскресенье
        LocalDate weekStart = weekEnd.minusDays(6); // Понедельник

        List<Long> userIds = userApi.getAllUserIds();

        for (Long userId : userIds) {
            try {
                // Вызываем транзакционный метод (this. нельзя, т.к. проигнорируется @Transactional, поэтому нужен self-inject, но для простоты пусть пока так)
                self.generateForUser(userId, weekStart, weekEnd);

            } catch (Exception e) {
                log.error("Ошибка генерации отчета для юзера {}: {}", userId, e.getMessage());
            }
        }
        log.info("Генерация еженедельных отчетов завершена.");
    }

    //Each user has his own transaction
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