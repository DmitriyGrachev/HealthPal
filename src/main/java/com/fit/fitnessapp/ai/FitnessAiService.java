package com.fit.fitnessapp.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit.fitnessapp.ai.application.service.DailyInsightResult;
import com.fit.fitnessapp.ai.application.service.DailyInsightService;
import com.fit.fitnessapp.ai.application.service.MonthlyReportService;
import com.fit.fitnessapp.ai.application.service.ReportSnapshotHasher;
import com.fit.fitnessapp.ai.application.service.TelegramAskAiService;
import com.fit.fitnessapp.ai.application.service.WeeklyReportService;
import com.fit.fitnessapp.api.MonthlyReportRequestedEvent;
import com.fit.fitnessapp.api.NutritionSyncedEvent;
import com.fit.fitnessapp.api.TelegramAiResponseEvent;
import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import com.fit.fitnessapp.api.TelegramTodayRequestedEvent;
import com.fit.fitnessapp.api.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.api.WorkoutImportedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    public FitnessAiService(
            DailyInsightService dailyInsightService,
            TelegramAskAiService telegramAskAiService,
            ApplicationEventPublisher eventPublisher,
            com.fit.fitnessapp.job.DurableJobUseCase durableJobUseCase,
            ObjectMapper objectMapper,
            WeeklyReportService weeklyReportService,
            MonthlyReportService monthlyReportService) {
        this.dailyInsightService = dailyInsightService;
        this.telegramAskAiService = telegramAskAiService;
        this.eventPublisher = eventPublisher;
        this.durableJobUseCase = durableJobUseCase;
        this.objectMapper = objectMapper;
        this.weeklyReportService = weeklyReportService;
        this.monthlyReportService = monthlyReportService;
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
        if (event.changed()) {
            enqueueDailyInsight(event.userId(), event.date(),
                    "nutrition|" + event.summaryHash() + '|' + event.entriesHash());
        }
    }

    @ApplicationModuleListener
    public void onWorkoutImported(WorkoutImportedEvent event) {
        log.info("AI module received WorkoutImportedEvent for user {} from {} to {}",
                event.userId(), event.fromDate(), event.toDate());
        String eventFingerprint = sha256(serializeJobPayload(event));
        for (LocalDate date : affectedWorkoutDates(event)) {
            enqueueDailyInsight(event.userId(), date, "workout|" + eventFingerprint);
        }
    }

    public DailyInsightResult generateDailyInsight(Long userId, LocalDate date) {
        return dailyInsightService.generate(userId, date);
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

    private void enqueueDailyInsight(Long userId, LocalDate date, String sourceFingerprint) {
        durableJobUseCase.createJob(
                AiDurableJobExecutor.DAILY_INSIGHT,
                userId,
                serializeJobPayload(new DailyInsightJobPayload(date)),
                "daily-insight:v1:%d:%s:%s".formatted(userId, date, sha256(sourceFingerprint)));
    }

    private String serializeJobPayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize AI durable job payload", e);
        }
    }

    private List<LocalDate> affectedWorkoutDates(WorkoutImportedEvent event) {
        if (event.affectedDates() != null && !event.affectedDates().isEmpty()) {
            return event.affectedDates();
        }
        if (event.fromDate() == null || event.toDate() == null || event.fromDate().isAfter(event.toDate())) {
            return List.of();
        }
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = event.fromDate(); !date.isAfter(event.toDate()); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    private String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
