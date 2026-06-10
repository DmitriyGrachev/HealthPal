package com.fit.fitnessapp.analytics;

import com.fit.fitnessapp.analytics.application.MonthlyReportOrchestrator;
import com.fit.fitnessapp.analytics.application.MonthlyReportTransactionService;
import com.fit.fitnessapp.auth.UserApi;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyReportOrchestratorTest {

    @Test
    void scheduledMonthlyReportUsesPreviousFullMonth() {
        UserApi userApi = mock(UserApi.class);
        MonthlyReportTransactionService transactionService = mock(MonthlyReportTransactionService.class);
        when(userApi.getAllUserIds()).thenReturn(List.of(42L));

        new MonthlyReportOrchestrator(userApi, transactionService).generateMonthlyReports();

        YearMonth previousMonth = YearMonth.now().minusMonths(1);
        LocalDate expectedStart = previousMonth.atDay(1);
        LocalDate expectedEnd = previousMonth.atEndOfMonth();
        verify(transactionService).generateForUser(42L, expectedStart, expectedEnd);
    }
}
