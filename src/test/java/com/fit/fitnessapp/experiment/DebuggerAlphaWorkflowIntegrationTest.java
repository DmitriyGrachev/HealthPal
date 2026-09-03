package com.fit.fitnessapp.experiment;

import com.fit.fitnessapp.ai.AiProperties;
import com.fit.fitnessapp.experiment.application.port.in.DebuggerWorkflowUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentDecisionUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentQueryUseCase;
import com.fit.fitnessapp.experiment.application.port.in.GoalQueryUseCase;
import com.fit.fitnessapp.experiment.application.port.in.InvestigationCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.InvestigationQueryUseCase;
import com.fit.fitnessapp.experiment.domain.EvaluationDecision;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.InvestigationStatus;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {"app.ai.experiment-draft-enabled=false", "app.memory.knowledge-events-enabled=false"})
class DebuggerAlphaWorkflowIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private DebuggerWorkflowUseCase workflow;

    @Autowired
    private InvestigationCommandUseCase investigationCommands;

    @Autowired
    private InvestigationQueryUseCase investigationQueries;

    @Autowired
    private GoalQueryUseCase goalQueries;

    @Autowired
    private ExperimentQueryUseCase experimentQueries;

    @Autowired
    private ExperimentDecisionUseCase decisions;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired private com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort knowledge;
    @Autowired private com.fit.fitnessapp.experiment.api.ExperimentEvaluationSource evaluationSources;
    @Autowired private com.fit.fitnessapp.knowledge.application.service.ExperimentResultClaimService resultClaims;
    @Autowired private com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimInspectorUseCase inspector;

    @AfterEach
    void removeFixtureUsers() {
        jdbc.update("DELETE FROM users WHERE username LIKE 'debugger-alpha-%'");
    }

    @ParameterizedTest
    @CsvSource({"6, KEEP, SUFFICIENT, SUPPORTED", "3, INCONCLUSIVE, INSUFFICIENT, PROPOSED"})
    void completesManualAlphaWorkflowWithAiDisabled(int checkInDays, String recommendation,
                                                   String quality, String verification) {
        assertThat(aiProperties.experimentDraftEnabled()).isFalse();
        assertThat(aiProperties.allowSensitiveExternalEgress()).isFalse();

        long userId = insertFixtureUser();
        DebuggerWorkflowUseCase.GoalResult goal = workflow.createGoal(
                userId, "Improve strength", "alpha-goal");
        assertThat(goal.status()).isEqualTo("DRAFT");
        assertThat(goal.aggregateVersion()).isZero();

        DebuggerWorkflowUseCase.GoalResult activeGoal = workflow.transitionGoal(
                userId, goal.goalId(), goal.aggregateVersion(), "ACTIVE", "alpha-goal-active");
        assertThat(activeGoal.status()).isEqualTo(GoalStatus.ACTIVE.name());
        assertThat(activeGoal.aggregateVersion()).isEqualTo(1L);

        var opened = investigationQueries.find(userId, goal.investigationId()).orElseThrow();
        assertThat(opened.status()).isEqualTo(InvestigationStatus.OPEN);
        var collecting = investigationCommands.transition(
                userId, opened.id(), "COLLECTING_BASELINE", opened.aggregateVersion(),
                "alpha-investigation-collecting", "begin baseline");
        assertThat(collecting.status()).isEqualTo(InvestigationStatus.COLLECTING_BASELINE);
        assertThat(collecting.aggregateVersion()).isEqualTo(1L);
        var ready = investigationCommands.transition(
                userId, collecting.id(), "READY_FOR_EXPERIMENT", collecting.aggregateVersion(),
                "alpha-investigation-ready", "baseline ready");
        assertThat(ready.status()).isEqualTo(InvestigationStatus.READY_FOR_EXPERIMENT);
        assertThat(ready.aggregateVersion()).isEqualTo(2L);

        LocalDate today = LocalDate.now(Clock.systemUTC());
        LocalDate baselineEnd = today.minusDays(7);
        LocalDate baselineStart = today.minusDays(14);
        DebuggerWorkflowUseCase.ExperimentResult draft = workflow.createExperiment(
                userId,
                new DebuggerWorkflowUseCase.ExperimentDraft(
                        goal.investigationId(), goal.goalId(), "A controlled strength intervention improves strength",
                        baselineStart, baselineEnd, 7, "Add one strength session", "Complete one extra session weekly",
                        "strength", "INCREASE", BigDecimal.valueOf(2), "Stop immediately for pain or dizziness"),
                "alpha-experiment-create");
        assertThat(draft.status()).isEqualTo(ExperimentStatus.DRAFT.name());
        assertThat(draft.aggregateVersion()).isZero();

        var proposed = workflow.transitionExperiment(
                userId, draft.experimentId(), draft.aggregateVersion(), "PROPOSED", "alpha-experiment-proposed");
        var accepted = workflow.transitionExperiment(
                userId, draft.experimentId(), proposed.aggregateVersion(), "ACCEPTED", "alpha-experiment-accepted");
        var active = workflow.transitionExperiment(
                userId, draft.experimentId(), accepted.aggregateVersion(), "ACTIVE", "alpha-experiment-active");
        assertThat(active.status()).isEqualTo(ExperimentStatus.ACTIVE.name());
        assertThat(active.aggregateVersion()).isEqualTo(3L);

        var experimenting = investigationCommands.transition(
                userId, ready.id(), "EXPERIMENTING", ready.aggregateVersion(),
                "alpha-investigation-experimenting", "start intervention");
        assertThat(experimenting.status()).isEqualTo(InvestigationStatus.EXPERIMENTING);
        assertThat(experimenting.aggregateVersion()).isEqualTo(3L);

        LocalDate interventionStart = baselineEnd.plusDays(1);
        assertThat(interventionStart.plusDays(6)).isEqualTo(today);
        for (int day = 0; day < checkInDays; day++) {
            DebuggerWorkflowUseCase.EvidenceResult checkIn = workflow.recordCheckIn(
                    userId,
                    new DebuggerWorkflowUseCase.CheckInDraft(
                            draft.experimentId(), interventionStart.plusDays(day), "UTC", "YES",
                            null, null, null, null, null),
                    "alpha-check-in-" + day);
            assertThat(checkIn.created()).isTrue();
        }

        DebuggerWorkflowUseCase.EvidenceResult outcome = workflow.recordOutcome(
                userId,
                new DebuggerWorkflowUseCase.OutcomeDraft(
                        draft.experimentId(), "strength", BigDecimal.valueOf(100), BigDecimal.valueOf(105),
                        "kg", 2, 2, "manual alpha outcome"),
                "alpha-outcome");
        assertThat(outcome.created()).isTrue();

        var completed = workflow.transitionExperiment(
                userId, draft.experimentId(), active.aggregateVersion(), "COMPLETED", "alpha-experiment-completed");
        DebuggerWorkflowUseCase.EvaluationResult evaluation = workflow.evaluate(
                userId, draft.experimentId(), completed.aggregateVersion(), "alpha-evaluation");
        assertThat(evaluation.recommendedDecision()).isEqualTo(recommendation);
        assertThat(evaluation.dataQuality()).isEqualTo(quality);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(knowledge.findAllByOwner(userId)).hasSize(1));
        var resultClaim = knowledge.findAllByOwner(userId).getFirst();
        assertThat(resultClaim.origin()).isEqualTo(com.fit.fitnessapp.knowledge.domain.ClaimOrigin.EXPERIMENT_RESULT);
        assertThat(resultClaim.verification().name()).isEqualTo(verification);
        assertThat(resultClaim.value().canonicalValue()).isEqualTo("POSITIVE");
        assertThat(resultClaim.evidence()).hasSize(checkInDays + 2); // evaluation, outcome, calculated check-ins
        var resultEvent = evaluationSources.find(userId, evaluation.evaluationId()).orElseThrow();
        for (int day = checkInDays; day < 7; day++) {
            workflow.recordCheckIn(userId, new DebuggerWorkflowUseCase.CheckInDraft(
                    draft.experimentId(), interventionStart.plusDays(day), "UTC", "YES",
                    null, null, null, null, null), "late-check-in-" + day);
        }
        assertThat(evaluationSources.find(userId, evaluation.evaluationId())).contains(resultEvent);
        resultClaims.project(resultEvent);
        assertThat(knowledge.findAllByOwner(userId)).hasSize(1);
        inspector.forget(userId, resultClaim.id(), resultClaim.aggregateVersion(), "forget-result");
        resultClaims.project(resultEvent);
        assertThat(knowledge.findAllByOwner(userId)).isEmpty();

        var decision = decisions.decide(
                userId, draft.experimentId(), evaluation.evaluationId(), EvaluationDecision.KEEP,
                "Keep the intervention", "alpha-decision");
        assertThat(decision.decision()).isEqualTo(EvaluationDecision.KEEP);

        var evaluated = workflow.transitionExperiment(
                userId, draft.experimentId(), completed.aggregateVersion(), "EVALUATED", "alpha-experiment-evaluated");
        assertThat(evaluated.status()).isEqualTo(ExperimentStatus.EVALUATED.name());
        assertThat(evaluated.aggregateVersion()).isEqualTo(5L);

        var resolved = investigationCommands.transition(
                userId, experimenting.id(), "RESOLVED", experimenting.aggregateVersion(),
                "alpha-investigation-resolved", "decision recorded");
        assertThat(resolved.status()).isEqualTo(InvestigationStatus.RESOLVED);
        assertThat(resolved.aggregateVersion()).isEqualTo(4L);

        assertThat(goalQueries.find(userId, goal.goalId()).orElseThrow().status()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(investigationQueries.find(userId, goal.investigationId()).orElseThrow().status())
                .isEqualTo(InvestigationStatus.RESOLVED);
        assertThat(experimentQueries.find(userId, draft.experimentId()).orElseThrow().status())
                .isEqualTo(ExperimentStatus.EVALUATED);
        assertThat(count("experiment_check_ins", userId, draft.experimentId())).isEqualTo(7L);
        assertThat(count("experiment_outcomes", userId, draft.experimentId())).isOne();
        assertThat(count("experiment_evaluations", userId, draft.experimentId())).isOne();
        assertThat(count("experiment_decisions", userId, draft.experimentId())).isOne();
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        resultClaims.project(resultEvent);
        assertThat(knowledge.findAllByOwner(userId)).isEmpty();
    }

    private long insertFixtureUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return jdbc.queryForObject("""
                INSERT INTO users (username, email, password)
                VALUES (?, ?, 'integration-pass')
                RETURNING id
                """, Long.class, "debugger-alpha-" + suffix, suffix + "@example.test");
    }

    private long count(String table, long userId, long experimentId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ? AND experiment_id = ?",
                Long.class, userId, experimentId);
    }
}
