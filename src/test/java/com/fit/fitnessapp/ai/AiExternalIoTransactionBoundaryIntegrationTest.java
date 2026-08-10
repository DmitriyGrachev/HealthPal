package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.application.service.AiContextService;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.api.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.ProfileUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AiExternalIoTransactionBoundaryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private FitnessAiService service;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private MoeOrchestrator moeOrchestrator;

    @MockitoBean
    private AiContextService aiContextService;

    @MockitoBean
    private UserNoteUseCase userNoteUseCase;

    @MockitoBean
    private ProfileUseCase profileUseCase;

    @MockitoBean
    private WeightHistoryUseCase weightHistoryUseCase;

    @MockitoBean
    private AiPromptRenderer promptRenderer;

    @MockitoBean
    private VectorStore vectorStore;

    @Test
    void aiProviderRunsOutsideTransactionAndResultPersists() {
        long userId = insertUser();
        WeeklyReportRequestedEvent event = weeklyEvent(userId);
        AtomicBoolean transactionActiveAtProvider = new AtomicBoolean(true);
        when(userNoteUseCase.getNotesByUserIdAndDateRange(eq(userId), any(), any())).thenReturn(List.of());
        when(profileUseCase.getProfileByUserId(userId)).thenReturn(Optional.empty());
        when(weightHistoryUseCase.getWeightHistoryByUserId(userId)).thenReturn(List.of());
        when(aiContextService.buildMemoryContext(eq(userId), anyString())).thenReturn("memory context");
        when(aiContextService.getRecentInsightsSummary(userId, com.fit.fitnessapp.api.InsightType.WEEKLY))
                .thenReturn("recent insights");
        when(promptRenderer.render(eq("weekly-report-v1.md"), any())).thenReturn("weekly prompt");
        when(moeOrchestrator.route(userId,
                new ClassifiedAiPrompt("weekly prompt", AiDataClass.SENSITIVE),
                MoeOrchestrator.AiTaskType.WEEKLY_REPORT))
                .thenAnswer(invocation -> {
                    transactionActiveAtProvider.set(
                            TransactionSynchronizationManager.isActualTransactionActive());
                    return validResponse(event.weekStart(), event.weekEnd());
                });

        service.generateWeeklyReport(event);

        assertThat(transactionActiveAtProvider).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_insights WHERE user_id = ? AND insight_type = 'WEEKLY'",
                Long.class,
                userId)).isOne();
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class,
                "ai" + suffix.substring(0, 8),
                "ai+" + suffix + "@example.test");
    }

    private WeeklyReportRequestedEvent weeklyEvent(long userId) {
        LocalDate start = LocalDate.of(2026, 7, 6);
        LocalDate end = LocalDate.of(2026, 7, 12);
        return new WeeklyReportRequestedEvent(
                userId,
                start,
                end,
                new WeeklyReportRequestedEvent.NutritionSnapshot(
                        14_000, 2_000, 140, 80, 220,
                        Map.of(start.toString(), new WeeklyReportRequestedEvent.DailyMacrosSnapshot(
                                2_000, 140, 80, 220))),
                new WeeklyReportRequestedEvent.WorkoutSnapshot(
                        3, 12_500, 1, 1_800, 320,
                        Map.of(start.toString(), 4_000.0)));
    }

    private NutritionInsightResponse validResponse(LocalDate start, LocalDate end) {
        return new NutritionInsightResponse(
                NutritionInsightResponse.ReportType.WEEKLY,
                new NutritionInsightResponse.Period(start, end),
                "Weekly summary",
                "Weekly Telegram summary",
                new NutritionInsightResponse.MacroAnalysis(
                        2_000, 140, 80, 220,
                        NutritionInsightResponse.CalorieBalance.MAINTENANCE,
                        NutritionInsightResponse.ProteinAdequacy.ADEQUATE),
                NutritionInsightResponse.WeightTrend.STALLING,
                List.of(),
                List.of(),
                List.of(),
                0.8f,
                0.9f);
    }
}
