package com.fit.fitnessapp.knowledge;

import com.fit.fitnessapp.experiment.application.port.out.*;
import com.fit.fitnessapp.experiment.domain.*;
import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimCommandUseCase;
import com.fit.fitnessapp.knowledge.context.*;
import com.fit.fitnessapp.knowledge.domain.*;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.springframework.test.context.TestPropertySource(properties = {
        "app.ai.experiment-draft-enabled=true", "app.ai.allow-sensitive-external-egress=true",
        "app.memory.knowledge-events-enabled=false"})
class UserContextIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired UserContextQuery contexts;
    @Autowired JdbcTemplate jdbc;
    @Autowired GoalRepositoryPort goals;
    @Autowired InvestigationRepositoryPort investigations;
    @Autowired ExperimentRepositoryPort experiments;
    @Autowired KnowledgeClaimCommandUseCase claims;
    @Autowired AiHypothesisWriter hypotheses;
    @Autowired com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimInspectorUseCase inspector;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.fit.fitnessapp.ai.adapter.out.experiment.AiExperimentDraftGenerator generator;
    @Autowired com.fit.fitnessapp.experiment.application.service.AlphaExperimentContextService alpha;
    @org.springframework.test.context.bean.override.mockito.MockitoBean(name = "openRouterChatClient")
    org.springframework.ai.chat.client.ChatClient client;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.fit.fitnessapp.ai.AiExecutionGuard execution;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.springframework.ai.vectorstore.VectorStore vectors;

    @Test
    void aiDraftPersistsOnlyProposedHypothesisAndFencesStaleSourceForgetAndDeletion() {
        long owner = user();
        Instant now = Instant.now().minusSeconds(10);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UserContext.ExperimentDraft context;
        var evidence = List.of(new ContextSlices.Source("NUTRITION_DAY", today.minusDays(3).toString(), 1, "a".repeat(64)));
        try {
            var investigation = investigations.insert(Investigation.create(owner, "Draft", "Question"));
            var goal = goals.insert(new Goal(null, owner, GoalType.PERFORMANCE, "Strength", GoalMetric.STRENGTH,
                    new TargetRange(100.0, 110.0, "kg"), GoalStatus.ACTIVE, null, 1, GoalSource.USER,
                    investigation.id(), null, true, 1, now, null, now));
            var experiment = experiments.insertExperiment(new Experiment(null, owner, investigation.id(), goal.id(),
                    new Hypothesis("A change improves strength"), today.minusDays(3), today.minusDays(2), 2,
                    new Intervention("Training", "One additional set"), "strength", List.of(),
                    List.of(new StopCondition("pain", "Stop on pain")), OutcomeDirection.INCREASE,
                    BigDecimal.ONE, ExperimentStatus.PROPOSED, 0, now, null, null, null, null, null, null, now));
            for (var date : List.of(today.minusDays(3), today.minusDays(2), today)) jdbc.update("""
                    INSERT INTO nutrition_source_state
                        (user_id, source_date, source_version, content_hash, present, lifecycle_epoch, created_at, updated_at)
                    SELECT id, ?, 1, ?, TRUE, lifecycle_epoch, ?, ? FROM users WHERE id = ?
                    """, date, "a".repeat(64), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), owner);
            context = (UserContext.ExperimentDraft) contexts.assemble(new UserContextRequest(owner,
                    ContextPurpose.EXPERIMENT_DRAFT, today.minusDays(3), today, goal.id(), experiment.id(), 0, 0));
            var draftRequest = com.fit.fitnessapp.experiment.spi.ExperimentDraftRequest.from(
                    alpha.assemble(owner, experiment.id()), "Could an additional set help?");
            var requestSpec = org.mockito.Mockito.mock(org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec.class,
                    org.mockito.Answers.RETURNS_DEEP_STUBS);
            org.mockito.Mockito.when(client.prompt()).thenReturn(requestSpec);
            org.mockito.Mockito.when(requestSpec.user(org.mockito.ArgumentMatchers.anyString())).thenReturn(requestSpec);
            org.mockito.Mockito.when(requestSpec.options(org.mockito.ArgumentMatchers.any(org.springframework.ai.openai.OpenAiChatOptions.Builder.class)))
                    .thenReturn(requestSpec);
            org.mockito.Mockito.when(requestSpec.call().content()).thenAnswer(invocation -> {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return """
                        {"hypothesis":"One set might help","interventions":[{"action":"Add one set","protocol":"One extra set weekly"}],
                         "stopConditions":[{"code":"pain","description":"Stop on pain"}],"evidenceRefIds":["E1"],
                         "outcomeDirection":"INCREASE","expectedChange":1,"meaningfulChange":1,"primaryMetric":"strength",
                         "rationale":"A bounded proposal for review"}
                        """;
            });
            org.mockito.Mockito.doAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(3)).get())
                    .when(execution).execute(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
            var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
            var generated = transaction.execute(status -> generator.generate(draftRequest));
            assertThat(generated).isPresent();
            assertThat(hypotheses.record(owner, context, "One set might help", evidence)).isTrue();
            assertThat(hypotheses.record(owner, context, "A different suggestion", evidence)).isFalse();
            var saved = jdbc.queryForMap("SELECT id, aggregate_version, origin, verification, confidence_basis, confidence_score, predicate FROM knowledge_claims WHERE user_id = ?", owner);
            assertThat(saved).containsEntry("origin", "AI_HYPOTHESIS").containsEntry("verification", "PROPOSED")
                    .containsEntry("predicate", "proposed_hypothesis").containsEntry("confidence_basis", "AI_MODEL");
            assertThat((BigDecimal) saved.get("confidence_score")).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM experiments WHERE id = ?", String.class, experiment.id())).isEqualTo("PROPOSED");
            transaction.executeWithoutResult(status -> {
                jdbc.update("UPDATE goals SET aggregate_version = aggregate_version + 1 WHERE id = ?", goal.id());
                assertThat(hypotheses.record(owner, context, "One set might help", evidence)).isFalse();
                status.setRollbackOnly();
            });
            transaction.executeWithoutResult(status -> {
                jdbc.update("UPDATE experiments SET aggregate_version = aggregate_version + 1 WHERE id = ?", experiment.id());
                assertThat(hypotheses.record(owner, context, "One set might help", evidence)).isFalse();
                status.setRollbackOnly();
            });
            transaction.executeWithoutResult(status -> {
                jdbc.update("UPDATE nutrition_source_state SET source_version = 2 WHERE user_id = ?", owner);
                assertThat(hypotheses.record(owner, context, "One set might help", evidence)).isFalse();
                status.setRollbackOnly();
            });
            inspector.forget(owner, ((Number) saved.get("id")).longValue(), ((Number) saved.get("aggregate_version")).longValue(), "forget-ai-draft");
            assertThat(hypotheses.record(owner, context, "One set might help", evidence)).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_claims WHERE user_id = ?", Long.class, owner)).isZero();
        } finally {
            jdbc.update("DELETE FROM users WHERE id = ?", owner);
        }
        assertThat(hypotheses.record(owner, context, "One set might help", evidence)).isFalse();
    }

    @Test
    void readsOwnedCanonicalStateWithoutWritingEvidenceOrUsageAndRejectsForeignTargets() {
        long owner = user();
        long other = user();
        Instant now = Instant.now().minusSeconds(10);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        try {
            var investigation = investigations.insert(Investigation.create(owner, "Context", "Question"));
            var goal = goals.insert(new Goal(null, owner, GoalType.PERFORMANCE, "Strength", GoalMetric.STRENGTH,
                    new TargetRange(100.0, 110.0, "kg"), GoalStatus.ACTIVE, null, 1, GoalSource.USER,
                    investigation.id(), null, true, 1, now, null, now));
            var experiment = experiments.insertExperiment(new Experiment(null, owner, investigation.id(), goal.id(),
                    new Hypothesis("A change improves strength"), today.minusDays(3), today.minusDays(2), 2,
                    new Intervention("Training", "One additional set"), "strength", List.of(),
                    List.of(new StopCondition("pain", "Stop on pain")), OutcomeDirection.INCREASE,
                    BigDecimal.ONE, ExperimentStatus.PROPOSED, 0, now, null, null, null, null, null, null, now));
            var constraint = claims.upsert(owner, KnowledgeClaim.create(owner, new ClaimSubject("user"),
                    new ClaimPredicate("constraint.training"), TypedClaimValue.text("No heavy squats"),
                    ClaimOrigin.USER_DECLARED, ClaimVerification.SUPPORTED, new ClaimSourceRef("NOTE", "constraint", 1),
                    now, null, null, new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_CONFIRMATION, "1"),
                    List.of(), now), 0, "context-constraint").orElseThrow();
            jdbc.update("""
                    INSERT INTO nutrition_source_state
                        (user_id, source_date, source_version, content_hash, present, lifecycle_epoch, created_at, updated_at)
                    SELECT id, ?, 1, ?, TRUE, lifecycle_epoch, ?, ? FROM users WHERE id = ?
                    """, today, "a".repeat(64), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), owner);
            var request = new UserContextRequest(owner, ContextPurpose.EXPERIMENT_DRAFT, today.minusDays(3), today,
                    goal.id(), experiment.id(), 2, 100);

            var result = (UserContext.ExperimentDraft) contexts.assemble(request);

            assertThat(result.goals()).extracting(ContextSlices.GoalContext::id).containsExactly(goal.id());
            assertThat(result.experiment().id()).isEqualTo(experiment.id());
            assertThat(result.verifiedConstraints()).extracting(ContextSlices.Claim::id).containsExactly(constraint.id());
            assertThat(result.observations()).extracting(ContextSlices.Observation::sourceType).containsExactly("NUTRITION_DAY");
            assertThat(result.metadata().projectionAvailable()).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM experiment_evidence_refs WHERE user_id = ?", Long.class, owner)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_claim_usage WHERE user_id = ?", Long.class, owner)).isZero();
            assertThatThrownBy(() -> contexts.assemble(new UserContextRequest(other, ContextPurpose.EXPERIMENT_DRAFT,
                    today.minusDays(3), today, null, experiment.id(), 0, 0))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> contexts.assemble(new UserContextRequest(other, ContextPurpose.TELEGRAM_ANSWER,
                    today.minusDays(3), today, goal.id(), null, 0, 0))).isInstanceOf(IllegalArgumentException.class);
            var empty = (UserContext.TelegramAnswer) contexts.assemble(new UserContextRequest(other,
                    ContextPurpose.TELEGRAM_ANSWER, today.minusDays(3), today, null, null, 0, 0));
            assertThat(empty.goals()).isEmpty();
            assertThat(empty.verifiedConstraints()).isEmpty();
            assertThat(empty.observations()).isEmpty();
        } finally {
            jdbc.update("DELETE FROM users WHERE id IN (?, ?)", owner, other);
        }
    }

    private long user() {
        return jdbc.queryForObject("""
                INSERT INTO users(username, email, password) VALUES (?, ?, 'test-only') RETURNING id
                """, Long.class, "context-" + UUID.randomUUID(), UUID.randomUUID() + "@example.test");
    }
}
