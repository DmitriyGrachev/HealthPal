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
import org.springframework.test.context.TestPropertySource;
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

@TestPropertySource(properties = "app.ai.allow-sensitive-external-egress=true")
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

    @Autowired private com.fit.fitnessapp.ai.application.service.AiAnswerDeliveryService answerDelivery;
    @Autowired private com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimCommandUseCase claimCommands;
    @Autowired private com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimInspectorUseCase inspector;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    void answerQueuesExactUsageAtomicallyAndRefusesStaleOrRevokedContext() {
        long owner = insertUser();
        long chat = 8_100_000L + owner;
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)", chat, owner, chat);
        try {
            var now = java.time.Instant.now();
            var candidate = com.fit.fitnessapp.knowledge.domain.KnowledgeClaim.create(owner,
                    new com.fit.fitnessapp.knowledge.domain.ClaimSubject("user"),
                    new com.fit.fitnessapp.knowledge.domain.ClaimPredicate("pattern"),
                    com.fit.fitnessapp.knowledge.domain.TypedClaimValue.text("an unconfirmed pattern"),
                    com.fit.fitnessapp.knowledge.domain.ClaimOrigin.AI_HYPOTHESIS,
                    com.fit.fitnessapp.knowledge.domain.ClaimVerification.PROPOSED,
                    new com.fit.fitnessapp.knowledge.domain.ClaimSourceRef("AI_DRAFT", "answer-fixture", 1),
                    now, null, null, new com.fit.fitnessapp.knowledge.domain.ClaimConfidenceBasis(
                            com.fit.fitnessapp.knowledge.domain.ClaimConfidenceBasis.Type.AI_MODEL, "0.8"), List.of(), now);
            var claim = claimCommands.upsert(owner, candidate, 0, "answer-claim").orElseThrow();
            var view = new com.fit.fitnessapp.knowledge.context.ContextSlices.Claim(claim.id(), claim.aggregateVersion(),
                    "user", "pattern", "TEXT", claim.value().canonicalValue(), null, "AI_HYPOTHESIS", "PROPOSED",
                    "AI_MODEL", claim.confidenceBasis().confidence(), new com.fit.fitnessapp.knowledge.context.ContextSlices.Source(
                            "AI_DRAFT", "answer-fixture", 1, claim.contentHash()), null, null,
                    com.fit.fitnessapp.knowledge.context.ContextFreshness.of(now, now, 30), List.of());
            var context = new AiContextService.PreparedContext("bounded context", List.of(view), true);
            when(aiContextService.prepareTelegramContext(owner)).thenReturn(context);
            when(promptRenderer.render(eq("telegram-ask-v2.md"), any())).thenReturn("bounded question");
            AtomicBoolean providerInTransaction = new AtomicBoolean(true);
            when(moeOrchestrator.route(eq(owner), any(), eq(MoeOrchestrator.AiTaskType.QUICK_ANALYSIS)))
                    .thenAnswer(invocation -> {
                        providerInTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
                        return new NutritionInsightResponse(null, null, "Tentative answer", "Tentative answer",
                                null, null, List.of(), List.of(), List.of(), 0.5f, 0.5f, List.of(claim.id()));
                    });
            service.onTelegramAskRequested(new com.fit.fitnessapp.api.TelegramAskRequestedEvent(owner, chat, "What is known?"));
            assertThat(providerInTransaction).isFalse();
            var queued = jdbc.queryForMap("SELECT id, text FROM telegram_delivery_outbox WHERE user_id = ?", owner);
            assertThat(queued.get("text").toString()).contains(
                    com.fit.fitnessapp.ai.application.service.AiAnswerDeliveryService.CONFLICT_WARNING,
                    com.fit.fitnessapp.ai.application.service.AiAnswerDeliveryService.UNCONFIRMED_WARNING, "Tentative answer");
            assertThat(jdbc.queryForMap("SELECT claim_id, claim_version, claim_content_hash, consumer_id FROM knowledge_claim_usage WHERE user_id = ?", owner))
                    .containsEntry("claim_id", claim.id()).containsEntry("claim_version", claim.aggregateVersion())
                    .containsEntry("claim_content_hash", claim.contentHash())
                    .containsEntry("consumer_id", "telegram-answer:" + queued.get("id"));
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                answerDelivery.deliver(owner, chat, "Rollback answer", context, List.of(claim.id()));
                var contradictory = com.fit.fitnessapp.knowledge.domain.KnowledgeClaim.create(owner,
                        candidate.subject(), candidate.predicate(),
                        com.fit.fitnessapp.knowledge.domain.TypedClaimValue.text("a different pattern"),
                        candidate.origin(), candidate.verification(),
                        new com.fit.fitnessapp.knowledge.domain.ClaimSourceRef("AI_DRAFT", "late-conflict", 1),
                        now, null, null, candidate.confidenceBasis(), List.of(), now);
                claimCommands.upsert(owner, contradictory, 0, "late-answer-conflict").orElseThrow();
                answerDelivery.deliver(owner, chat, "General answer after conflict",
                        new AiContextService.PreparedContext("", List.of(), false), List.of());
                assertThat(jdbc.queryForObject("SELECT text FROM telegram_delivery_outbox WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                        String.class, owner)).startsWith(com.fit.fitnessapp.ai.application.service.AiAnswerDeliveryService.CONFLICT_WARNING);
                status.setRollbackOnly();
            });
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM telegram_delivery_outbox WHERE user_id = ?", Long.class, owner)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_claim_usage WHERE user_id = ?", Long.class, owner)).isOne();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> answerDelivery.deliver(owner, chat, "Unknown source", context, List.of(claim.id() + 1000)))
                    .isInstanceOf(IllegalArgumentException.class);
            inspector.dispute(owner, claim.id(), claim.aggregateVersion(), "dispute-answer-source");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> answerDelivery.deliver(owner, chat, "Stale answer", context, List.of(claim.id())))
                    .isInstanceOf(com.fit.fitnessapp.knowledge.context.ContextUseRejectedException.class);
            jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", owner);
            assertThat(answerDelivery.deliver(owner, chat, "No link", new AiContextService.PreparedContext("", List.of(), false), List.of())).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_claim_usage WHERE user_id = ?", Long.class, owner)).isOne();
        } finally {
            jdbc.update("DELETE FROM users WHERE id = ?", owner);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_claim_usage WHERE user_id = ?", Long.class, owner)).isZero();
    }

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
