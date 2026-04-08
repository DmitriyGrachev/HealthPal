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
    private final WeeklyReportTransactionService weeklyReportTransactionService;

    // Every monday в 09:00
    @Scheduled(cron = "0 0 9 * * MON")
    public void generateWeeklyReports() {
        log.info("Запуск генерации еженедельных отчетов...");

        LocalDate weekEnd = LocalDate.now().minusDays(1); // Воскресенье
        LocalDate weekStart = weekEnd.minusDays(6); // Понедельник

        List<Long> userIds = userApi.getAllUserIds();

        for (Long userId : userIds) {
            try {
                weeklyReportTransactionService.generateForUser(userId, weekStart, weekEnd);

            } catch (Exception e) {
                log.error("Ошибка генерации отчета для юзера {}: {}", userId, e.getMessage());
            }
        }
        log.info("Генерация еженедельных отчетов завершена.");
    }
}