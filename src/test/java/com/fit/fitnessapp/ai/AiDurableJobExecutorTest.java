package com.fit.fitnessapp.ai;

import tools.jackson.databind.ObjectMapper;
import com.fit.fitnessapp.ai.application.service.DailyInsightResult;
import com.fit.fitnessapp.api.DomainEventMetadata;
import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.api.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.JobStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiDurableJobExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FitnessAiService fitnessAiService = mock(FitnessAiService.class);
    private final AiDurableJobExecutor executor = new AiDurableJobExecutor(fitnessAiService, objectMapper);

    @Test
    void executesLegacyDailyInsightFromDateOnlyPayload() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 9);
        String payload = "{\"date\":\"2026-08-09\"}";
        when(fitnessAiService.generateDailyInsight(42L, date))
                .thenReturn(DailyInsightResult.generated());

        executor.execute(job(AiDurableJobExecutor.DAILY_INSIGHT, payload));

        verify(fitnessAiService).generateDailyInsight(42L, date);
    }

    @Test
    void executesVersionedDailyInsightWithTriggerAndTreatsStaleAsSuccess() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 9);
        Instant occurredAt = Instant.parse("2026-08-09T12:00:00Z");
        DomainSourceState state = new DomainSourceState(
                42L,
                "NUTRITION_DAY",
                date,
                3L,
                true,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                UUID.fromString("8bf60f6f-a7ee-4b71-b82c-848e09277d76"),
                1,
                occurredAt,
                occurredAt);
        DomainEventMetadata trigger = state.metadata(
                UUID.fromString("68e9ca4f-49f5-4891-98d8-16de24b4ddde"));
        String payload = objectMapper.writeValueAsString(new DailyInsightJobPayload(date, trigger));
        when(fitnessAiService.generateDailyInsight(42L, date, trigger))
                .thenReturn(DailyInsightResult.skippedStale());

        executor.execute(job(AiDurableJobExecutor.DAILY_INSIGHT, payload));

        verify(fitnessAiService).generateDailyInsight(42L, date, trigger);
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
