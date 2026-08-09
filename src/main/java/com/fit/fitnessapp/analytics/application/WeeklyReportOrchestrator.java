package com.fit.fitnessapp.analytics.application;

import com.fit.fitnessapp.auth.UserApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Clock;
import java.util.List;

@Service
public class WeeklyReportOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportOrchestrator.class);

    private final UserApi userApi;
    private final WeeklyReportTransactionService weeklyReportTransactionService;
    private final Clock clock;

    @Autowired
    public WeeklyReportOrchestrator(UserApi userApi, WeeklyReportTransactionService service, Clock clock) {
        this.userApi = userApi;
        this.weeklyReportTransactionService = service;
        this.clock = clock;
    }

    public WeeklyReportOrchestrator(UserApi userApi, WeeklyReportTransactionService service) {
        this(userApi, service, Clock.systemUTC());
    }

    // Every Monday at 09:00.
    @Scheduled(cron = "0 0 9 * * MON")
    public void generateWeeklyReports() {
        log.info("Weekly report generation started");

        LocalDate weekEnd = LocalDate.now(clock).minusDays(1);
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
