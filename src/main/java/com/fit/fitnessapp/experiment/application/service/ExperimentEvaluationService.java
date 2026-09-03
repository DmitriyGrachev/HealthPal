package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.application.port.in.ExperimentEvaluationUseCase;
import com.fit.fitnessapp.experiment.application.port.in.EvidenceCommandResult;
import com.fit.fitnessapp.experiment.application.port.out.CommandReceiptPort;
import com.fit.fitnessapp.experiment.application.port.out.EvidenceRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.InvestigationRepositoryPort;
import com.fit.fitnessapp.experiment.api.ExperimentEvaluationSource;
import com.fit.fitnessapp.experiment.domain.AdherenceStatus;
import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;
import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.CalculationInput;
import com.fit.fitnessapp.experiment.domain.CalculationResult;
import com.fit.fitnessapp.experiment.domain.Evaluation;
import com.fit.fitnessapp.experiment.domain.EvaluationAlreadyExistsException;
import com.fit.fitnessapp.experiment.domain.EvaluationCalculator;
import com.fit.fitnessapp.experiment.domain.EvaluationInsufficientEvidenceException;
import com.fit.fitnessapp.experiment.domain.ExperimentNotCompletedException;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Transactional application boundary for the pure V1 deterministic Evaluation. */
@Service
public class ExperimentEvaluationService implements ExperimentEvaluationUseCase {
    private static final String AGGREGATE = "EVALUATION";

    private final ExperimentRepositoryPort experiments;
    private final EvidenceRepositoryPort evidence;
    private final CommandReceiptPort receipts;
    private final InvestigationRepositoryPort investigations;
    private final EvaluationCalculator calculator;
    private final ExperimentMetrics metrics;
    private final Clock clock;
    private final ExperimentEvaluationSource evaluationSources;
    private final ApplicationEventPublisher events;

    @Autowired
    public ExperimentEvaluationService(ExperimentRepositoryPort experiments,
                                        EvidenceRepositoryPort evidence,
                                        CommandReceiptPort receipts,
                                        InvestigationRepositoryPort investigations,
                                        ExperimentMetrics metrics,
                                        ExperimentEvaluationSource evaluationSources,
                                        ApplicationEventPublisher events) {
        this(experiments, evidence, receipts, investigations, new EvaluationCalculator(), metrics,
                Clock.systemUTC(), evaluationSources, events);
    }

    public ExperimentEvaluationService(ExperimentRepositoryPort experiments,
                                        EvidenceRepositoryPort evidence,
                                        CommandReceiptPort receipts,
                                        InvestigationRepositoryPort investigations,
                                        EvaluationCalculator calculator,
                                        ExperimentMetrics metrics,
                                        Clock clock,
                                        ExperimentEvaluationSource evaluationSources,
                                        ApplicationEventPublisher events) {
        this.experiments = experiments;
        this.evidence = evidence;
        this.receipts = receipts;
        this.investigations = investigations;
        this.calculator = calculator;
        this.metrics = metrics;
        this.clock = clock;
        this.evaluationSources = evaluationSources;
        this.events = events;
    }

    @Override
    @Transactional
    public Evaluation evaluate(Long userId, Long experimentId, long expectedVersion,
                               String idempotencyKey) {
        return evaluateWithStatus(userId, experimentId, expectedVersion, idempotencyKey).value();
    }

    @Override
    @Transactional
    public EvidenceCommandResult<Evaluation> evaluateWithStatus(
            Long userId, Long experimentId, long expectedVersion, String idempotencyKey) {
        requireOwnerAndId(userId, experimentId);
        requireVersion(expectedVersion);
        requireKey(idempotencyKey);
        String fingerprint = CommandRequestFingerprint.evaluation(experimentId, expectedVersion);
        Optional<CommandReceiptPort.CommandReceipt> previous =
                receipts.find(userId, AGGREGATE, idempotencyKey);
        if (previous.isPresent()) {
            return new EvidenceCommandResult<>(replay(userId, experimentId, previous.get(), expectedVersion,
                    fingerprint), false);
        }

        var experiment = experiments.findExperimentByUserIdAndIdForUpdate(userId, experimentId)
                .orElseThrow(ExperimentNotFoundException::new);
        if (experiment.aggregateVersion() != expectedVersion) {
            throw new AggregateVersionConflictException();
        }
        if (experiment.status() != ExperimentStatus.COMPLETED) {
            throw new ExperimentNotCompletedException();
        }
        var outcome = evidence.findPrimaryOutcomeByUserIdAndExperimentId(userId, experimentId)
                .orElseThrow(EvaluationInsufficientEvidenceException::new);
        Instant investigationCreatedAt = investigations.findByUserIdAndId(userId, experiment.investigationId())
                .orElseThrow(ExperimentNotFoundException::new)
                .createdAt();

        Optional<Evaluation> existing = evidence.findEvaluationByUserIdAndExperimentId(userId, experimentId);
        if (existing.isPresent()) {
            Object priorVersion = existing.get().calculationInputs().get("expectedVersion");
            if (priorVersion instanceof Number number && number.longValue() == expectedVersion) {
                reserveReceipt(userId, existing.get().id(), idempotencyKey, expectedVersion, fingerprint,
                        existing.get().evaluatedAt());
                return new EvidenceCommandResult<>(existing.get(), false);
            }
            throw new EvaluationAlreadyExistsException();
        }

        Instant evaluatedAt = Instant.now(clock);
        LocalDate interventionStart = experiment.baselineEndDate().plusDays(1);
        LocalDate interventionEnd = interventionStart.plusDays(experiment.durationDays() - 1L);
        var checkIns = evidence.findCheckInsByUserIdAndExperimentIdAndLocalDateBetween(
                userId, experimentId, interventionStart, interventionEnd);
        var countsByStatus = checkIns.stream().collect(java.util.stream.Collectors.groupingBy(
                ExperimentCheckIn::adherence, java.util.stream.Collectors.counting()));
        int yes = countsByStatus.getOrDefault(AdherenceStatus.YES, 0L).intValue();
        int no = countsByStatus.getOrDefault(AdherenceStatus.NO, 0L).intValue();
        int partial = countsByStatus.getOrDefault(AdherenceStatus.PARTIAL, 0L).intValue();
        CalculationInput input = new CalculationInput(
                experiment.durationDays(), yes, no, partial,
                experiment.durationDays() - yes - no - partial, outcome.baselineValue(), outcome.observedValue(),
                outcome.baselineSampleCount(), outcome.observedSampleCount(),
                experiment.outcomeDirection(), experiment.meaningfulChange(), outcome.observedAt(),
                evaluatedAt, EvaluationCalculator.DEFAULT_MAX_FRESHNESS_DAYS,
                evidence.confounderAssessment(userId, experimentId),
                evidence.stopConditionTriggered(userId, experimentId));
        CalculationResult result = calculator.calculate(input);
        Map<String, Object> calculationInputs = new LinkedHashMap<>(result.calculationInputs());
        calculationInputs.put("expectedVersion", expectedVersion);
        // The calculator and provenance use the same immutable rows, never a later window query.
        calculationInputs.put("evaluationOutcomeId", outcome.id());
        calculationInputs.put("evaluationCheckInIds", checkIns.stream().map(ExperimentCheckIn::id).sorted().toList());
        Evaluation evaluation = new Evaluation(null, userId, experimentId, result.formulaVersion(),
                result.recommendedDecision(), result.dataQuality(), result.observedEffect(),
                result.confounderAssessment(), result.effectDelta(), result.effectThreshold(),
                result.coverage(), result.adherence(), result.freshnessDays(), calculationInputs,
                result.reasonCodes(), evaluatedAt);

        EvidenceRepositoryPort.EvaluationWriteResult write = evidence.insertEvaluation(evaluation);
        Evaluation stored = write.evaluation();
        if (write.status() == EvidenceRepositoryPort.WriteStatus.DUPLICATE) {
            Object prior = stored.calculationInputs().get("expectedVersion");
            if (!(prior instanceof Number number) || number.longValue() != expectedVersion) {
                throw new EvaluationAlreadyExistsException();
            }
        }
        reserveReceipt(userId, stored.id(), idempotencyKey, expectedVersion, fingerprint, stored.evaluatedAt());
        if (write.status() == EvidenceRepositoryPort.WriteStatus.INSERTED) {
            events.publishEvent(evaluationSources.find(userId, stored.id())
                    .orElseThrow(() -> new IllegalStateException("evaluation provenance is unavailable")));
            metrics.evaluated(stored.recommendedDecision());
            metrics.evaluationDecision(stored.recommendedDecision());
            metrics.timeToEvaluation(investigationCreatedAt, stored.evaluatedAt());
        }
        return new EvidenceCommandResult<>(stored,
                write.status() == EvidenceRepositoryPort.WriteStatus.INSERTED);
    }

    private void reserveReceipt(Long userId, Long aggregateId, String key, long resultVersion,
                                String fingerprint, Instant createdAt) {
        if (receipts.insert(userId, AGGREGATE, aggregateId, key, resultVersion, fingerprint, createdAt)) {
            return;
        }
        CommandReceiptPort.CommandReceipt winner = receipts.find(userId, AGGREGATE, key)
                .orElseThrow(AggregateVersionConflictException::new);
        if (!fingerprint.equals(winner.requestFingerprint())
                || !aggregateId.equals(winner.aggregateId())
                || winner.resultVersion() != resultVersion) {
            throw new IdempotencyConflictException();
        }
    }

    private Evaluation replay(Long userId, Long experimentId,
                              CommandReceiptPort.CommandReceipt receipt, long expectedVersion,
                              String fingerprint) {
        if (!AGGREGATE.equals(receipt.aggregateType())
                || receipt.resultVersion() != expectedVersion
                || !fingerprint.equals(receipt.requestFingerprint())) {
            throw new IdempotencyConflictException();
        }
        Evaluation stored = evidence.findEvaluationByUserIdAndId(userId, receipt.aggregateId())
                .orElseThrow(ExperimentNotFoundException::new);
        if (!experimentId.equals(stored.experimentId())) {
            throw new IdempotencyConflictException();
        }
        return stored;
    }

    private static void requireOwnerAndId(Long userId, Long experimentId) {
        if (userId == null || userId < 1 || experimentId == null || experimentId < 1) {
            throw new IllegalArgumentException("owner and experiment identifiers must be positive");
        }
    }

    private static void requireVersion(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must be between 1 and 128 characters");
        }
    }
}
