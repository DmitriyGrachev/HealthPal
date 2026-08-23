package com.fit.fitnessapp.experiment.application.port.out;

import com.fit.fitnessapp.experiment.domain.Evaluation;
import com.fit.fitnessapp.experiment.domain.ConfounderAssessment;
import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;
import com.fit.fitnessapp.experiment.domain.Outcome;
import com.fit.fitnessapp.experiment.domain.UserDecision;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Owner-scoped persistence boundary for the evidence side of an Experiment.
 *
 * <p>Implementations must perform the unique-key checks in the same database
 * transaction as the insert.  A duplicate result is deliberately typed: the
 * application layer can distinguish a convergent replay from a payload
 * conflict without inspecting SQL exceptions.</p>
 */
public interface EvidenceRepositoryPort {
    Optional<ExperimentCheckIn> findCheckInByUserIdAndId(Long userId, Long checkInId);

    Optional<ExperimentCheckIn> findCheckInByUserIdAndExperimentIdAndLocalDate(
            Long userId, Long experimentId, LocalDate localDate);

    CheckInWriteResult insertCheckIn(ExperimentCheckIn checkIn);

    CheckInSummary countCheckIns(Long userId, Long experimentId,
                                 LocalDate fromInclusive, LocalDate toInclusive);

    Optional<Outcome> findOutcomeByUserIdAndId(Long userId, Long outcomeId);

    Optional<Outcome> findPrimaryOutcomeByUserIdAndExperimentId(Long userId, Long experimentId);

    OutcomeWriteResult insertOutcome(Outcome outcome);

    Optional<Evaluation> findEvaluationByUserIdAndId(Long userId, Long evaluationId);

    Optional<Evaluation> findEvaluationByUserIdAndExperimentId(Long userId, Long experimentId);

    EvaluationWriteResult insertEvaluation(Evaluation evaluation);

    Optional<UserDecision> findDecisionByUserIdAndId(Long userId, Long decisionId);

    Optional<UserDecision> findDecisionByUserIdAndExperimentId(Long userId, Long experimentId);

    DecisionWriteResult insertDecision(UserDecision decision);

    /** Structured confounder input; free-text notes are never classified here. */
    default ConfounderAssessment confounderAssessment(Long userId, Long experimentId) {
        return ConfounderAssessment.NONE;
    }

    /** Structured stop-condition input; implementations may derive it from lifecycle evidence. */
    default boolean stopConditionTriggered(Long userId, Long experimentId) {
        return false;
    }

    /** A duplicate local-date write returns the row that owns that date. */
    record CheckInWriteResult(WriteStatus status, ExperimentCheckIn checkIn) {
        public CheckInWriteResult {
            if (status == null || checkIn == null) {
                throw new IllegalArgumentException("check-in write result is incomplete");
            }
        }
    }

    /** A duplicate primary-outcome write returns the row that owns the experiment. */
    record OutcomeWriteResult(WriteStatus status, Outcome outcome) {
        public OutcomeWriteResult {
            if (status == null || outcome == null) {
                throw new IllegalArgumentException("outcome write result is incomplete");
            }
        }
    }

    /** A duplicate evaluation write returns the single evaluation for the experiment. */
    record EvaluationWriteResult(WriteStatus status, Evaluation evaluation) {
        public EvaluationWriteResult {
            if (status == null || evaluation == null) {
                throw new IllegalArgumentException("evaluation write result is incomplete");
            }
        }
    }

    /** A duplicate decision write returns the single decision for the experiment. */
    record DecisionWriteResult(WriteStatus status, UserDecision decision) {
        public DecisionWriteResult {
            if (status == null || decision == null) {
                throw new IllegalArgumentException("decision write result is incomplete");
            }
        }
    }

    enum WriteStatus {
        INSERTED,
        DUPLICATE
    }

    /** Counts are calculated from the inclusive intervention window. */
    record CheckInSummary(int yesDays, int noDays, int partialDays, int unknownDays) {
        public CheckInSummary {
            if (yesDays < 0 || noDays < 0 || partialDays < 0 || unknownDays < 0) {
                throw new IllegalArgumentException("check-in counts must not be negative");
            }
        }

        public int knownDays() {
            return yesDays + noDays + partialDays;
        }
    }
}
