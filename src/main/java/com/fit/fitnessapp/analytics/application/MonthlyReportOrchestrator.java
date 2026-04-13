package com.fit.fitnessapp.analytics.application;

import com.fit.fitnessapp.auth.UserApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyReportOrchestrator {

    private final UserApi userApi;
    private final MonthlyReportTransactionService monthlyReportTransactionService;

    // Первый день каждого месяца в 10:00
    @Scheduled(cron = "0 0 10 1 * *")
    public void generateMonthlyReports() {
        log.info("Запуск генерации ежемесячных отчетов...");

        // Берём прошлый месяц целиком
        //YearMonth lastMonth = YearMonth.now().minusMonths(1);
        YearMonth lastMonth = YearMonth.now();
        LocalDate monthStart = lastMonth.atDay(1);
        LocalDate monthEnd   = lastMonth.atEndOfMonth();

        List<Long> userIds = userApi.getAllUserIds();

        for (Long userId : userIds) {
            try {
                monthlyReportTransactionService.generateForUser(userId, monthStart, monthEnd);
            } catch (Exception e) {
                log.error("Ошибка генерации месячного отчета для юзера {}: {}", userId, e.getMessage());
            }
        }

        log.info("Генерация ежемесячных отчетов завершена.");
    }
}