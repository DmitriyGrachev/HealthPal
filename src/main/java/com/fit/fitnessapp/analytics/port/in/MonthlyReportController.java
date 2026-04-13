package com.fit.fitnessapp.analytics.port.in;

import com.fit.fitnessapp.analytics.application.MonthlyReportOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/month")
@RequiredArgsConstructor
public class MonthlyReportController {

    private final MonthlyReportOrchestrator monthlyReportOrchestrator;

    @GetMapping
    public void getMonthlyReport() {
        monthlyReportOrchestrator.generateMonthlyReports();
    }
}