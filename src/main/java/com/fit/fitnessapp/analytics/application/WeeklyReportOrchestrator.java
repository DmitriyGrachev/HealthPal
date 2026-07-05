package com.fit.fitnessapp.analytics.application;

import com.fit.fitnessapp.auth.UserApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportOrchestrator {

    private final UserApi userApi;
    private final WeeklyReportTransactionService weeklyReportTransactionService;

    // Every Monday at 09:00.
    @Scheduled(cron = "0 0 9 * * MON")
    public void generateWeeklyReports() {
        log.info("Weekly report generation started");

        LocalDate weekEnd = LocalDate.now().minusDays(1);
        LocalDate weekStart = weekEnd.minusDays(6);

        List<Long> userIds = userApi.getAllUserIds();

        for (Long userId : userIds) {
            try {
                weeklyReportTransactionService.generateForUser(userId, weekStart, weekEnd);
            } catch (Exception e) {
                log.error("Weekly report generation failed userId={} errorCode={}",
                        userId, e.getClass().getSimpleName());
            }
        }

        log.info("Weekly report generation completed");
    }
}
