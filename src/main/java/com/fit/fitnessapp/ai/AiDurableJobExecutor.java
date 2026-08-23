package com.fit.fitnessapp.ai;

import tools.jackson.databind.ObjectMapper;
import com.fit.fitnessapp.ai.application.service.DailyInsightResult;
import com.fit.fitnessapp.api.MonthlyReportRequestedEvent;
import com.fit.fitnessapp.api.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiDurableJobExecutor implements DurableJobExecutor {

    public static final String DAILY_INSIGHT = "DAILY_INSIGHT";
    public static final String WEEKLY_REPORT = "WEEKLY_REPORT";
    public static final String MONTHLY_REPORT = "MONTHLY_REPORT";

    private final FitnessAiService fitnessAiService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String jobType) {
        return DAILY_INSIGHT.equals(jobType)
                || WEEKLY_REPORT.equals(jobType)
                || MONTHLY_REPORT.equals(jobType);
    }

    @Override
    public void execute(DurableJobDto job) throws Exception {
        switch (job.jobType()) {
            case DAILY_INSIGHT -> executeDaily(job);
            case WEEKLY_REPORT -> fitnessAiService.generateWeeklyReport(
                    objectMapper.readValue(job.payloadJson(), WeeklyReportRequestedEvent.class));
            case MONTHLY_REPORT -> fitnessAiService.generateMonthlyReport(
                    objectMapper.readValue(job.payloadJson(), MonthlyReportRequestedEvent.class));
            default -> throw new IllegalArgumentException("Unsupported AI job type: " + job.jobType());
        }
    }

    private void executeDaily(DurableJobDto job) throws Exception {
        DailyInsightJobPayload payload = objectMapper.readValue(
                job.payloadJson(), DailyInsightJobPayload.class);
        DailyInsightResult result = payload.trigger() == null
                ? fitnessAiService.generateDailyInsight(job.userId(), payload.date())
                : fitnessAiService.generateDailyInsight(job.userId(), payload.date(), payload.trigger());
        if (result == null || result.status() == DailyInsightResult.Status.AI_FAILED) {
            String errorCode = result == null ? "NULL_RESULT" : result.errorCode();
            throw new IllegalStateException("Daily insight execution failed: " + errorCode);
        }
    }
}
