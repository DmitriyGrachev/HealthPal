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

class UserContextIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired UserContextQuery contexts;
    @Autowired JdbcTemplate jdbc;
    @Autowired GoalRepositoryPort goals;
    @Autowired InvestigationRepositoryPort investigations;
    @Autowired ExperimentRepositoryPort experiments;
    @Autowired KnowledgeClaimCommandUseCase claims;

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
