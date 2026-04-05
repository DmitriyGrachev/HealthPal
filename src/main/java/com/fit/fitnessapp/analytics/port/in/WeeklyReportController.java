package com.fit.fitnessapp.analytics.port.in;

import com.fit.fitnessapp.analytics.application.WeeklyReportOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/week")
@RequiredArgsConstructor
public class WeeklyReportController {

   private final WeeklyReportOrchestrator weeklyReportOrchestrator;

   @GetMapping
   public void getWeeklyReport(){
        weeklyReportOrchestrator.generateWeeklyReports();
   }
}
