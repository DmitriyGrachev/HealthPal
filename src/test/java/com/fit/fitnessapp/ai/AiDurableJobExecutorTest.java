package com.fit.fitnessapp.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fit.fitnessapp.ai.application.service.DailyInsightResult;
import com.fit.fitnessapp.api.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.JobStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiDurableJobExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final FitnessAiService fitnessAiService = mock(FitnessAiService.class);
    private final AiDurableJobExecutor executor = new AiDurableJobExecutor(fitnessAiService, objectMapper);

    @Test
    void executesDailyInsightFromStoredPayload() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 9);
        String payload = objectMapper.writeValueAsString(new DailyInsightJobPayload(date));
        when(fitnessAiService.generateDailyInsight(42L, date))
                .thenReturn(DailyInsightResult.generated());

        executor.execute(job(AiDurableJobExecutor.DAILY_INSIGHT, payload));

        verify(fitnessAiService).generateDailyInsight(42L, date);
    }

    @Test
    void executesWeeklyReportFromStoredSnapshot() throws Exception {
        LocalDate start = LocalDate.of(2026, 8, 3);
        WeeklyReportRequestedEvent event = new WeeklyReportRequestedEvent(
                42L,
                start,
                start.plusDays(6),
                new WeeklyReportRequestedEvent.NutritionSnapshot(
                        14000, 2000, 130, 70, 210, Map.of()),
                new WeeklyReportRequestedEvent.WorkoutSnapshot(3, 12000, Map.of()));
        String payload = objectMapper.writeValueAsString(event);

        executor.execute(job(AiDurableJobExecutor.WEEKLY_REPORT, payload));

        verify(fitnessAiService).generateWeeklyReport(event);
    }

    private DurableJobDto job(String type, String payload) {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        return new DurableJobDto(
                20L, type, 42L, JobStatus.RUNNING, 1, 3,
                null, null, payload, "ai-key", now, now);
    }
}
