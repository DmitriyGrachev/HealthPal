package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.application.service.DailyInsightResult;
import com.fit.fitnessapp.ai.application.service.DailyInsightService;
import com.fit.fitnessapp.ai.application.service.MonthlyReportService;
import com.fit.fitnessapp.ai.application.service.ReportSnapshotHasher;
import com.fit.fitnessapp.ai.application.service.TelegramAskAiService;
import com.fit.fitnessapp.ai.application.service.WeeklyReportService;
import com.fit.fitnessapp.api.MonthlyReportRequestedEvent;
import com.fit.fitnessapp.api.DomainEventMetadata;
import com.fit.fitnessapp.api.NutritionSyncedEvent;
import com.fit.fitnessapp.api.TelegramAiResponseEvent;
import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import com.fit.fitnessapp.api.TelegramTodayRequestedEvent;
import com.fit.fitnessapp.api.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.api.WorkoutImportedEvent;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionSourceStateQueryPort;
import com.fit.fitnessapp.workout.application.port.in.WorkoutSourceStateQueryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class FitnessAiService {

    static final String TODAY_NO_DATA_MESSAGE =
            "I don't have nutrition data for today yet. Log or sync your food first, then try /today again.";
    static final String TODAY_FALLBACK_MESSAGE =
            "Sorry, I couldn't generate today's insight right now. Please try again later.";

    private final DailyInsightService dailyInsightService;
    private final TelegramAskAiService telegramAskAiService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.fit.fitnessapp.job.DurableJobUseCase durableJobUseCase;
    private final ObjectMapper objectMapper;
    private final WeeklyReportService weeklyReportService;
    private final MonthlyReportService monthlyReportService;
    private final NutritionSourceStateQueryPort nutritionSourceStateQueryPort;
    private final WorkoutSourceStateQueryPort workoutSourceStateQueryPort;

    public FitnessAiService(
            DailyInsightService dailyInsightService,
            TelegramAskAiService telegramAskAiService,
            ApplicationEventPublisher eventPublisher,
            com.fit.fitnessapp.job.DurableJobUseCase durableJobUseCase,
            ObjectMapper objectMapper,
            WeeklyReportService weeklyReportService,
            MonthlyReportService monthlyReportService,
            NutritionSourceStateQueryPort nutritionSourceStateQueryPort,
            WorkoutSourceStateQueryPort workoutSourceStateQueryPort) {
        this.dailyInsightService = dailyInsightService;
        this.telegramAskAiService = telegramAskAiService;
        this.eventPublisher = eventPublisher;
        this.durableJobUseCase = durableJobUseCase;
        this.objectMapper = objectMapper;
        this.weeklyReportService = weeklyReportService;
        this.monthlyReportService = monthlyReportService;
        this.nutritionSourceStateQueryPort = nutritionSourceStateQueryPort;
        this.workoutSourceStateQueryPort = workoutSourceStateQueryPort;
    }

    @EventListener
    public void onTelegramTodayRequested(TelegramTodayRequestedEvent event) {
        log.info("AI request received userId={} taskType=DAILY_INSIGHT source=telegram", event.userId());
        DailyInsightResult result;
        try {
            result = dailyInsightService.generateOrPublishExisting(event.userId(), event.date());
        } catch (Exception e) {
            log.warn("AI request failed userId={} taskType=DAILY_INSIGHT source=telegram errorCode={}",
                    event.userId(), e.getClass().getSimpleName());
            publishTodayResponse(event, TODAY_FALLBACK_MESSAGE);
            return;
        }
        if (result == null || result.status() == DailyInsightResult.Status.AI_FAILED) {
            publishTodayResponse(event, TODAY_FALLBACK_MESSAGE);
        } else if (result.status() == DailyInsightResult.Status.NO_SNAPSHOT) {
            publishTodayResponse(event, TODAY_NO_DATA_MESSAGE);
        }
    }

    @EventListener
    public void onTelegramAskRequested(TelegramAskRequestedEvent event) {
        telegramAskAiService.answer(event);
    }

    @ApplicationModuleListener
    public void onNutritionSynced(NutritionSyncedEvent event) {
        log.info("AI module received NutritionSyncedEvent for user {} on {}", event.userId(), event.date());
        if (event.metadata() == null) {
            return;
        }
        if (!hasExpectedIdentity(
                event.metadata(), event.userId(), event.date(), "NUTRITION_DAY")) {
            return;
        }
        var current = nutritionSourceStateQueryPort.findCurrent(event.userId(), event.date());
        if (current.isEmpty() || !current.get().matches(event.metadata())) {
            return;
        }
        enqueueDailyInsight(event.userId(), event.date(), event.metadata());
    }

    @ApplicationModuleListener
    public void onWorkoutImported(WorkoutImportedEvent event) {
        log.info("AI module received WorkoutImportedEvent for user {} from {} to {}",
                event.userId(), event.fromDate(), event.toDate());
        if (event.metadata() == null) {
            return;
        }
        LocalDate sourceDate = sourceDate(event.metadata());
        if (sourceDate == null
                || !Objects.equals(event.fromDate(), sourceDate)
                || !Objects.equals(event.toDate(), sourceDate)
                || !List.of(sourceDate).equals(event.affectedDates())
                || !hasExpectedIdentity(
                        event.metadata(), event.userId(), sourceDate, "WORKOUT_DAY")) {
            return;
        }
        var current = workoutSourceStateQueryPort.findCurrent(event.userId(), sourceDate);
        if (current.isEmpty() || !current.get().matches(event.metadata())) {
            return;
        }
        enqueueDailyInsight(event.userId(), sourceDate, event.metadata());
    }

    public DailyInsightResult generateDailyInsight(Long userId, LocalDate date) {
        return dailyInsightService.generate(userId, date);
    }

    public DailyInsightResult generateDailyInsight(
            Long userId,
            LocalDate date,
            DomainEventMetadata trigger) {
        return dailyInsightService.generate(userId, date, trigger);
    }

    @ApplicationModuleListener
    public void onWeeklyReportRequested(WeeklyReportRequestedEvent event) {
        log.info("AI module received WeeklyReportRequestedEvent for user {}, week starting {}",
                event.userId(), event.weekStart());
        durableJobUseCase.createJob(
                AiDurableJobExecutor.WEEKLY_REPORT,
                event.userId(),
                serializeJobPayload(event),
                "weekly-report:v1:%d:%s:%s".formatted(
                        event.userId(), event.weekStart(), ReportSnapshotHasher.weekly(event)));
    }

    public void generateWeeklyReport(WeeklyReportRequestedEvent event) {
        weeklyReportService.generate(event);
    }

    @ApplicationModuleListener
    public void onMonthlyReportRequested(MonthlyReportRequestedEvent event) {
        log.info("AI module received MonthlyReportRequestedEvent for user {}, month {} - {}",
                event.userId(), event.monthStart(), event.monthEnd());
        durableJobUseCase.createJob(
                AiDurableJobExecutor.MONTHLY_REPORT,
                event.userId(),
                serializeJobPayload(event),
                "monthly-report:v1:%d:%s:%s".formatted(
                        event.userId(), event.monthStart(), ReportSnapshotHasher.monthly(event)));
    }

    public void generateMonthlyReport(MonthlyReportRequestedEvent event) {
        monthlyReportService.generate(event);
    }

    private void publishTodayResponse(TelegramTodayRequestedEvent event, String message) {
        eventPublisher.publishEvent(new TelegramAiResponseEvent(event.userId(), event.chatId(), message));
    }

    private void enqueueDailyInsight(Long userId, LocalDate date, DomainEventMetadata metadata) {
        durableJobUseCase.createJob(
                AiDurableJobExecutor.DAILY_INSIGHT,
                userId,
                serializeJobPayload(new DailyInsightJobPayload(date, metadata)),
                "daily-insight:v2:%d:%s:%s:%d:%s".formatted(
                        userId,
                        metadata.sourceType(),
                        date,
                        metadata.sourceVersion(),
                        metadata.eventId()));
    }

    private boolean hasExpectedIdentity(
            DomainEventMetadata metadata,
            Long eventUserId,
            LocalDate eventDate,
            String expectedSourceType) {
        LocalDate metadataDate = sourceDate(metadata);
        return metadataDate != null
                && Objects.equals(eventUserId, metadata.userId())
                && Objects.equals(eventDate, metadataDate)
                && expectedSourceType.equals(metadata.sourceType());
    }

    private LocalDate sourceDate(DomainEventMetadata metadata) {
        try {
            return LocalDate.parse(metadata.sourceId());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String serializeJobPayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize AI durable job payload", e);
        }
    }

}
