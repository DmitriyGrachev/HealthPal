package com.fit.fitnessapp.ai.adapter.out.experiment;

import com.fit.fitnessapp.experiment.spi.ExperimentDraft;
import com.fit.fitnessapp.experiment.spi.ExperimentDraftRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates untrusted structured output before it becomes an editable experiment draft.
 *
 * <p>The validator deliberately copies lifecycle and measurement fields from the deterministic
 * request. A model can propose wording and a bounded intervention only; it cannot alter the
 * experiment identity, dates, metric, direction, or meaningful-change threshold.</p>
 */
@Component
public final class AiExperimentDraftValidator {

    private static final int MAX_HYPOTHESIS_LENGTH = 4_000;
    private static final int MAX_ACTION_LENGTH = 200;
    private static final int MAX_PROTOCOL_LENGTH = 2_000;
    private static final int MAX_STOP_CODE_LENGTH = 64;
    private static final int MAX_STOP_DESCRIPTION_LENGTH = 500;
    private static final int MAX_RATIONALE_LENGTH = 4_000;

    private static final String INVALID_REQUEST = "invalid experiment draft request";
    private static final String INVALID_CANDIDATE = "invalid experiment draft candidate";
    private static final String INVALID_INTERVENTION = "experiment draft must contain exactly one intervention";
    private static final String INVALID_STOP_CONDITIONS = "experiment draft stop conditions are invalid";
    private static final String INVALID_EVIDENCE = "experiment draft evidence references are invalid";
    private static final String UNSAFE_TEXT = "experiment draft contains unsafe medical text";
    private static final String INVALID_ARITHMETIC = "experiment draft change is inconsistent with its direction";
    private static final String INVALID_STOP_SAFETY = "experiment draft must contain a safe stop condition";
    private static final String INVALID_TEXT = "experiment draft text is invalid";

    private static final Pattern MEDICAL_PATTERN = Pattern.compile(
            "\\b(?:diagnos(?:e|is|ed|ing)?|treat(?:ment|ing|ed)?|prescri(?:be|bed|bing|ption)?)\\b"
                    + "|\\bmedication\\b|\\bmedical\\s+(?:advice|care|treatment|diagnosis)\\b"
                    + "|\\b(?:tendinitis|tendonitis|arthritis|bursitis|sprain|fracture|infection|syndrome)\\b"
                    + "|\\b(?:ibuprofen|naproxen|aspirin|acetaminophen|paracetamol|painkillers?|nsaids?)\\b"
                    + "|диагностик|диагноз|лечени|лечить|лечащ|назначени|назначить|рецепт|лекарств|медикамент",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern UNSAFE_STOP_PATTERN = Pattern.compile(
            "\\bignore\\s+(?:the\\s+)?pain\\b"
                    + "|\\bpush\\s+through(?:\\s+the)?\\s+pain\\b"
                    + "|\\bcontinue\\s+despite\\b"
                    + "|\\bcontinue(?:\\s+training)?\\s+(?:through|with)\\s+(?:pain|discomfort|symptoms?)\\b"
                    + "|\\bdo\\s+not\\s+stop\\b"
                    + "|\\b(?:never|don['’]t)\\s+stop\\b"
                    + "|\\bpain\\s+is\\s+not\\s+(?:a\\s+)?reason\\s+to\\s+stop\\b"
                    + "|игнорируй\\s+боль"
                    + "|тренируйся\\s+через\\s+боль"
                    + "|продолжай\\s+несмотря"
                    + "|продолжай(?:\\s+тренироваться)?\\s+через\\s+(?:боль|дискомфорт)"
                    + "|не\\s+останавливайся",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern SAFE_STOP_PATTERN = Pattern.compile(
            "\\b(?:stop|pause|abort|discontinue|hold|rest|break|"
                    + "seek\\s+(?:medical\\s+)?help|consult\\s+(?:a\\s+)?(?:doctor|clinician|medical\\s+professional)|"
                    + "contact\\s+(?:a\\s+)?(?:doctor|clinician)|emergency\\s+services|call\\s+emergency)\\b"
                    + "|останов(?:ись|иться|ить|ка)?|прекрат(?:и|ить|ить\\s+тренировку)?"
                    + "|пауза|отдых|перерыв|обрат(?:ись|иться)\\s+к\\s+(?:врачу|доктору|медицинскому\\s+специалисту)"
                    + "|медицинск(?:ая|ую)\\s+помощь|скорая\\s+помощь",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    /** Structured JSON shape accepted from a model provider. */
    public record Candidate(
            String hypothesis,
            List<InterventionCandidate> interventions,
            List<StopConditionCandidate> stopConditions,
            List<String> evidenceRefIds,
            String outcomeDirection,
            BigDecimal expectedChange,
            BigDecimal meaningfulChange,
            String primaryMetric,
            String rationale) {
    }

    public record InterventionCandidate(String action, String protocol) {
    }

    public record StopConditionCandidate(String code, String description) {
    }

    /**
     * Validates and turns provider output into a pure, user-editable draft.
     *
     * @throws IllegalArgumentException when the request or provider output violates a safety
     *                                  or grounding invariant
     */
    public ExperimentDraft validate(ExperimentDraftRequest request, Candidate candidate) {
        requireRequest(request);
        if (candidate == null) {
            throw new IllegalArgumentException(INVALID_CANDIDATE);
        }
        validateDirectionAndMetric(request, candidate);
        validateMeaningfulChange(request, candidate);
        validateExpectedChange(request, candidate);
        validateText(candidate);

        ExperimentDraft.InterventionDraft intervention = validateIntervention(candidate.interventions());
        List<ExperimentDraft.StopConditionDraft> stopConditions = validateStopConditions(candidate.stopConditions());
        List<ExperimentDraftRequest.EvidenceView> evidenceRefs = validateEvidence(request, candidate.evidenceRefIds());

        return new ExperimentDraft(
                request.investigationId(),
                request.goalId(),
                request.experimentId(),
                request.experimentVersion(),
                normalizedRequired(candidate.hypothesis(), MAX_HYPOTHESIS_LENGTH),
                intervention,
                stopConditions,
                evidenceRefs,
                normalizedRequired(candidate.rationale(), MAX_RATIONALE_LENGTH),
                request.baselineStartDate(),
                request.baselineEndDate(),
                request.durationDays(),
                request.primaryMetric(),
                request.outcomeDirection(),
                request.meaningfulChange());
    }

    private void requireRequest(ExperimentDraftRequest request) {
        if (request == null
                || request.investigationId() < 1
                || request.goalId() < 1
                || request.experimentId() < 1
                || request.experimentVersion() < 0
                || request.baselineStartDate() == null
                || request.baselineEndDate() == null
                || request.baselineEndDate().isBefore(request.baselineStartDate())
                || request.durationDays() < 1
                || request.durationDays() > 90
                || isBlank(request.primaryMetric())
                || request.outcomeDirection() == null
                || request.meaningfulChange() == null
                || request.meaningfulChange().signum() <= 0
                || request.evidenceRefs() == null) {
            throw new IllegalArgumentException(INVALID_REQUEST);
        }
    }

    private void validateDirectionAndMetric(ExperimentDraftRequest request, Candidate candidate) {
        if (candidate.outcomeDirection() == null
                || !request.outcomeDirection().name().equals(candidate.outcomeDirection())
                || candidate.primaryMetric() == null
                || !request.primaryMetric().trim().equalsIgnoreCase(candidate.primaryMetric().trim())) {
            throw new IllegalArgumentException(INVALID_CANDIDATE);
        }
    }

    private void validateMeaningfulChange(ExperimentDraftRequest request, Candidate candidate) {
        if (candidate.meaningfulChange() == null
                || candidate.meaningfulChange().compareTo(request.meaningfulChange()) != 0) {
            throw new IllegalArgumentException(INVALID_ARITHMETIC);
        }
    }

    private void validateExpectedChange(ExperimentDraftRequest request, Candidate candidate) {
        BigDecimal expectedChange = candidate.expectedChange();
        BigDecimal threshold = request.meaningfulChange();
        if (expectedChange == null) {
            throw new IllegalArgumentException(INVALID_ARITHMETIC);
        }

        boolean valid = switch (request.outcomeDirection()) {
            case INCREASE -> expectedChange.compareTo(threshold) >= 0;
            case DECREASE -> expectedChange.compareTo(threshold.negate()) <= 0;
            case MAINTAIN -> expectedChange.abs().compareTo(threshold) < 0;
        };
        if (!valid) {
            throw new IllegalArgumentException(INVALID_ARITHMETIC);
        }
    }

    private void validateText(Candidate candidate) {
        if (isBlank(candidate.hypothesis())
                || isBlank(candidate.rationale())
                || candidate.hypothesis().trim().length() > MAX_HYPOTHESIS_LENGTH
                || candidate.rationale().trim().length() > MAX_RATIONALE_LENGTH) {
            throw new IllegalArgumentException(INVALID_TEXT);
        }

        StringBuilder output = new StringBuilder(candidate.hypothesis())
                .append('\n').append(candidate.rationale());
        appendInterventionText(output, candidate.interventions());
        appendStopText(output, candidate.stopConditions());

        String text = output.toString();
        if (MEDICAL_PATTERN.matcher(text).find()) {
            throw new IllegalArgumentException(UNSAFE_TEXT);
        }
        if (UNSAFE_STOP_PATTERN.matcher(text).find()) {
            throw new IllegalArgumentException(UNSAFE_TEXT);
        }
    }

    private void appendInterventionText(StringBuilder output, List<InterventionCandidate> interventions) {
        if (interventions == null) {
            return;
        }
        for (InterventionCandidate intervention : interventions) {
            if (intervention != null) {
                output.append('\n').append(intervention.action()).append('\n').append(intervention.protocol());
            }
        }
    }

    private void appendStopText(StringBuilder output, List<StopConditionCandidate> stopConditions) {
        if (stopConditions == null) {
            return;
        }
        for (StopConditionCandidate condition : stopConditions) {
            if (condition != null) {
                output.append('\n').append(condition.code()).append('\n').append(condition.description());
            }
        }
    }

    private ExperimentDraft.InterventionDraft validateIntervention(
            List<InterventionCandidate> interventions) {
        if (interventions == null || interventions.size() != 1 || interventions.get(0) == null) {
            throw new IllegalArgumentException(INVALID_INTERVENTION);
        }
        InterventionCandidate candidate = interventions.get(0);
        String action = normalizedRequired(candidate.action(), MAX_ACTION_LENGTH);
        String protocol = normalizedRequired(candidate.protocol(), MAX_PROTOCOL_LENGTH);
        return new ExperimentDraft.InterventionDraft(action, protocol);
    }

    private List<ExperimentDraft.StopConditionDraft> validateStopConditions(
            List<StopConditionCandidate> conditions) {
        if (conditions == null || conditions.isEmpty() || conditions.size() > 16
                || conditions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(INVALID_STOP_CONDITIONS);
        }

        boolean hasSafeStop = false;
        List<ExperimentDraft.StopConditionDraft> result = new java.util.ArrayList<>(conditions.size());
        for (StopConditionCandidate condition : conditions) {
            String code = normalizedRequired(condition.code(), MAX_STOP_CODE_LENGTH);
            String description = normalizedRequired(condition.description(), MAX_STOP_DESCRIPTION_LENGTH);
            hasSafeStop |= SAFE_STOP_PATTERN.matcher(code + "\n" + description).find();
            result.add(new ExperimentDraft.StopConditionDraft(code, description));
        }
        if (!hasSafeStop) {
            throw new IllegalArgumentException(INVALID_STOP_SAFETY);
        }
        return List.copyOf(result);
    }

    private List<ExperimentDraftRequest.EvidenceView> validateEvidence(
            ExperimentDraftRequest request, List<String> evidenceRefIds) {
        if (evidenceRefIds == null || evidenceRefIds.isEmpty()) {
            throw new IllegalArgumentException(INVALID_EVIDENCE);
        }
        Map<String, ExperimentDraftRequest.EvidenceView> permitted = request.evidenceRefs().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableMap(
                        ExperimentDraftRequest.EvidenceView::referenceId,
                        Function.identity(),
                        (first, ignored) -> first));
        Set<String> distinct = new HashSet<>();
        List<ExperimentDraftRequest.EvidenceView> selected = new java.util.ArrayList<>(evidenceRefIds.size());
        for (String id : evidenceRefIds) {
            if (isBlank(id) || !distinct.add(id) || !permitted.containsKey(id)) {
                throw new IllegalArgumentException(INVALID_EVIDENCE);
            }
            selected.add(permitted.get(id));
        }
        return List.copyOf(selected);
    }

    private String normalizedRequired(String value, int maxLength) {
        if (isBlank(value) || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(INVALID_TEXT);
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
