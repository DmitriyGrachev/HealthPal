package com.fit.fitnessapp.analytics.application;

import com.fit.fitnessapp.auth.UserApi;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MonthlyReportOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportOrchestrator.class);

    private final UserApi userApi;
    private final MonthlyReportTransactionService monthlyReportTransactionService;

    // First day of every month at 10:00.
    @Scheduled(cron = "0 0 10 1 * *")
    public void generateMonthlyReports() {
        log.info("Monthly report generation started");

        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        LocalDate monthStart = lastMonth.atDay(1);
        LocalDate monthEnd = lastMonth.atEndOfMonth();

        List<Long> userIds = userApi.getAllUserIds();

        for (Long userId : userIds) {
            try {
                monthlyReportTransactionService.generateForUser(userId, monthStart, monthEnd);
            } catch (Exception e) {
                log.error("Monthly report generation failed userId={} errorCode={}",
                        userId, e.getClass().getSimpleName());
            }
        }

        log.info("Monthly report generation completed");
    }
}
