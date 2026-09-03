package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.application.port.out.CommandReceiptPort;
import com.fit.fitnessapp.experiment.application.port.out.EvidenceRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.InvestigationRepositoryPort;
import com.fit.fitnessapp.experiment.domain.AdherenceStatus;
import com.fit.fitnessapp.experiment.domain.CheckInSource;
import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;
import org.springframework.context.ApplicationEventPublisher;
import com.fit.fitnessapp.experiment.domain.ConfounderAssessment;
import com.fit.fitnessapp.experiment.domain.Evaluation;
import com.fit.fitnessapp.experiment.domain.EvaluationCalculator;
import com.fit.fitnessapp.experiment.domain.EvaluationInsufficientEvidenceException;
import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.ExperimentNotCompletedException;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.Hypothesis;
import com.fit.fitnessapp.experiment.domain.Intervention;
import com.fit.fitnessapp.experiment.domain.Outcome;
import com.fit.fitnessapp.experiment.domain.OutcomeDirection;
import com.fit.fitnessapp.experiment.domain.OutcomeSource;
import com.fit.fitnessapp.experiment.domain.StopCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExperimentEvaluationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-23T10:00:00Z");

    private ExperimentRepositoryPort experiments;
    private InvestigationRepositoryPort investigations;
    private EvidenceRepositoryPort evidence;
    private CommandReceiptPort receipts;
    private ExperimentMetrics metrics;
    private ExperimentEvaluationService service;
    private ApplicationEventPublisher events;

    @BeforeEach
    void setUp() {
        experiments = mock(ExperimentRepositoryPort.class);
        investigations = mock(InvestigationRepositoryPort.class);
        evidence = mock(EvidenceRepositoryPort.class);
        receipts = mock(CommandReceiptPort.class);
        metrics = mock(ExperimentMetrics.class);
        events = mock(ApplicationEventPublisher.class);
        service = new ExperimentEvaluationService(experiments, evidence, receipts, investigations,
                new EvaluationCalculator(), metrics, Clock.fixed(NOW, ZoneOffset.UTC),
                new ExperimentEvaluationSourceService(evidence, experiments), events);
        when(receipts.find(any(), any(), any())).thenReturn(Optional.empty());
        when(receipts.insert(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(true);
    }

    @Test
    void refusesEvaluationBeforeCompletionWithoutCreatingEvidence() {
        Experiment active = experiment(ExperimentStatus.ACTIVE, 4L);
        when(experiments.findExperimentByUserIdAndIdForUpdate(42L, 7L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.evaluate(42L, 7L, 4L, "eval-1"))
                .isInstanceOf(ExperimentNotCompletedException.class);

        verify(evidence, never()).findPrimaryOutcomeByUserIdAndExperimentId(any(), any());
        verify(evidence, never()).insertEvaluation(any());
    }

    @Test
    void refusesEvaluationWithoutPrimaryOutcome() {
        Experiment completed = experiment(ExperimentStatus.COMPLETED, 4L);
        when(experiments.findExperimentByUserIdAndIdForUpdate(42L, 7L)).thenReturn(Optional.of(completed));
        when(evidence.findPrimaryOutcomeByUserIdAndExperimentId(42L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evaluate(42L, 7L, 4L, "eval-1"))
                .isInstanceOf(EvaluationInsufficientEvidenceException.class);

        verify(evidence, never()).insertEvaluation(any());
    }

    @Test
    void persistsDeterministicInputsAndRecordsMetricsOnlyForNewEvaluation() {
        Experiment completed = experiment(ExperimentStatus.COMPLETED, 4L);
        Outcome outcome = new Outcome(8L, 42L, 7L, "weight", BigDecimal.valueOf(100),
                BigDecimal.valueOf(102), "kg", 2, 2, NOW.minusSeconds(86_400),
                OutcomeSource.MANUAL, null, NOW);
        when(experiments.findExperimentByUserIdAndIdForUpdate(42L, 7L)).thenReturn(Optional.of(completed));
        when(evidence.findPrimaryOutcomeByUserIdAndExperimentId(42L, 7L)).thenReturn(Optional.of(outcome));
        when(evidence.findOutcomeByUserIdAndId(42L, 8L)).thenReturn(Optional.of(outcome));
        when(experiments.findExperimentByUserIdAndId(42L, 7L)).thenReturn(Optional.of(completed));
        when(investigations.findByUserIdAndId(42L, 11L)).thenReturn(
                Optional.of(new com.fit.fitnessapp.experiment.domain.Investigation(
                        11L, 42L, "test", "test problem",
                        com.fit.fitnessapp.experiment.domain.InvestigationStatus.OPEN, 0L, NOW, NOW)));
        when(evidence.confounderAssessment(42L, 7L)).thenReturn(ConfounderAssessment.NONE);
        var checkIns = java.util.stream.IntStream.range(0, 10).mapToObj(day -> new ExperimentCheckIn(
                20L + day, 42L, 7L, completed.baselineEndDate().plusDays(day + 1L), ZoneOffset.UTC,
                null, null, day < 8 ? AdherenceStatus.YES : day == 8 ? AdherenceStatus.NO : AdherenceStatus.PARTIAL,
                null, null, null, null, null, null, CheckInSource.MANUAL, NOW, NOW)).toList();
        when(evidence.findCheckInsByUserIdAndExperimentIdAndLocalDateBetween(eq(42L), eq(7L), any(), any()))
                .thenReturn(checkIns);
        checkIns.forEach(checkIn -> when(evidence.findCheckInByUserIdAndId(42L, checkIn.id()))
                .thenReturn(Optional.of(checkIn)));
        when(evidence.insertEvaluation(any())).thenAnswer(invocation -> {
            Evaluation requested = invocation.getArgument(0);
            Evaluation stored = new Evaluation(9L, requested.userId(), requested.experimentId(),
                    requested.formulaVersion(), requested.recommendedDecision(), requested.dataQuality(),
                    requested.observedEffect(), requested.confounderAssessment(), requested.effectDelta(),
                    requested.effectThreshold(), requested.coverage(), requested.adherence(),
                    requested.freshnessDays(), requested.calculationInputs(), requested.reasonCodes(),
                    requested.evaluatedAt());
            when(evidence.findEvaluationByUserIdAndId(42L, 9L)).thenReturn(Optional.of(stored));
            return new EvidenceRepositoryPort.EvaluationWriteResult(
                    EvidenceRepositoryPort.WriteStatus.INSERTED, stored);
        });

        Evaluation evaluation = service.evaluate(42L, 7L, 4L, "eval-1");

        assertThat(evaluation.recommendedDecision().name()).isEqualTo("KEEP");
        assertThat(evaluation.calculationInputs()).containsEntry("expectedVersion", 4L);
        assertThat(evaluation.calculationInputs()).containsEntry("evaluationOutcomeId", 8L)
                .containsEntry("evaluationCheckInIds", checkIns.stream().map(ExperimentCheckIn::id).toList());
        verify(events).publishEvent(any(com.fit.fitnessapp.experiment.api.ExperimentEvaluationCompletedEvent.class));
        verify(metrics).evaluated(evaluation.recommendedDecision());
        verify(metrics).evaluationDecision(evaluation.recommendedDecision());
        verify(metrics).timeToEvaluation(NOW, evaluation.evaluatedAt());
    }

    private static Experiment experiment(ExperimentStatus status, long version) {
        Instant accepted = NOW.minusSeconds(3 * 86_400L);
        Instant started = NOW.minusSeconds(2 * 86_400L);
        Instant completed = status == ExperimentStatus.COMPLETED ? NOW.minusSeconds(86_400) : null;
        return new Experiment(7L, 42L, 11L, 12L, new Hypothesis("walking helps"),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7), 10,
                new Intervention("walk", "daily"), "weight", List.of(),
                List.of(new StopCondition("PAIN", "stop")), OutcomeDirection.INCREASE,
                BigDecimal.ONE, status, version, NOW.minusSeconds(5 * 86_400L),
                accepted, started, null, null, completed, null,
                NOW);
    }
}
