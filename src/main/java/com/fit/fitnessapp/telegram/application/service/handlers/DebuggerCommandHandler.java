package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.experiment.application.port.in.DebuggerWorkflowUseCase;
import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Channel adapter for the manual debugger workflow. */
@Component
public final class DebuggerCommandHandler implements CommandHandler {

    private static final String INVESTIGATION_ID = "investigationId";
    private static final String GOAL_ID = "goalId";
    private static final String GOAL_VERSION = "goalVersion";
    private static final String EXPERIMENT_ID = "experimentId";
    private static final String EXPERIMENT_VERSION = "experimentVersion";
    private static final String LAST_UPDATE_ID = "lastDebuggerUpdateId";

    private static final Set<String> ADHERENCE_VALUES = Set.of("YES", "NO", "PARTIAL", "UNKNOWN");
    private static final Set<String> OUTCOME_DIRECTIONS = Set.of("INCREASE", "DECREASE", "MAINTAIN");
    private static final Map<String, String> EXPERIMENT_TRANSITIONS = Map.of(
            "propose", "PROPOSED",
            "accept", "ACCEPTED",
            "start", "ACTIVE",
            "pause", "PAUSED",
            "resume", "ACTIVE",
            "complete", "COMPLETED",
            "abort", "ABORTED");
    private static final BigDecimal MAX_EVIDENCE_VALUE = new BigDecimal("1000000000");
    private static final BigDecimal MAX_MEANINGFUL_CHANGE = new BigDecimal("1000000");

    private final TelegramBotService bot;
    private final ConversationStateUseCase states;
    private final UserTimeApi userTime;
    private final DebuggerWorkflowUseCase workflow;

    public DebuggerCommandHandler(
            TelegramBotService bot,
            ConversationStateUseCase states,
            UserTimeApi userTime,
            DebuggerWorkflowUseCase workflow) {
        this.bot = bot;
        this.states = states;
        this.userTime = userTime;
        this.workflow = workflow;
    }

    @Override
    public boolean canHandle(InboundCommand command) {
        return switch (command.type()) {
            case GOAL, EXPERIMENT, CHECKIN, OUTCOME, EVALUATE -> true;
            default -> false;
        };
    }

    @Override
    public void handle(InboundCommand command) {
        if (command.userId() == null) {
            respond(command, TelegramMessages.COMMAND_INVALID);
            return;
        }
        try {
            switch (command.type()) {
                case GOAL -> handleGoal(command);
                case EXPERIMENT -> handleExperiment(command);
                case CHECKIN -> handleCheckIn(command);
                case OUTCOME -> handleOutcome(command);
                case EVALUATE -> handleEvaluation(command);
                default -> respond(command, TelegramMessages.COMMAND_INVALID);
            }
        } catch (RuntimeException exception) {
            respond(command, TelegramMessages.COMMAND_INVALID);
        }
    }

    private void handleGoal(InboundCommand command) {
        String payload = requiredText(command.payload(), 160);
        DebuggerWorkflowUseCase.GoalResult result;
        if ("activate".equals(payload.toLowerCase(Locale.ROOT))) {
            Map<String, Object> data = stateData(command);
            if (alreadyProcessed(command, data)) {
                return;
            }
            result = workflow.transitionGoal(
                    command.userId(),
                    positiveId(data, GOAL_ID),
                    version(data, GOAL_VERSION),
                    "ACTIVE",
                    command.idempotencyKey());
        } else {
            result = workflow.createGoal(command.userId(), payload, command.idempotencyKey());
        }

        persistAndRespond(command, Map.of(
                        INVESTIGATION_ID, positive(result.investigationId()),
                        GOAL_ID, positive(result.goalId()),
                        GOAL_VERSION, nonNegative(result.aggregateVersion()),
                        LAST_UPDATE_ID, command.updateId()),
                TelegramMessages.goalUpdated(
                        result.investigationId(), result.goalId(), result.status()));
    }

    private void handleExperiment(InboundCommand command) {
        Map<String, Object> data = stateData(command);
        if (alreadyProcessed(command, data)) {
            return;
        }

        String transition = EXPERIMENT_TRANSITIONS.get(command.payload().toLowerCase(Locale.ROOT));
        DebuggerWorkflowUseCase.ExperimentResult result;
        if (transition != null) {
            result = workflow.transitionExperiment(
                    command.userId(),
                    positiveId(data, EXPERIMENT_ID),
                    version(data, EXPERIMENT_VERSION),
                    transition,
                    command.idempotencyKey());
        } else {
            String[] parts = parts(command.payload(), 9, 9);
            int baselineDays = positiveInt(parts[4], 90);
            int durationDays = positiveInt(parts[5], 90);
            String direction = enumValue(parts[6], OUTCOME_DIRECTIONS);
            BigDecimal meaningfulChange = positiveDecimal(parts[7], MAX_MEANINGFUL_CHANGE);
            LocalDate baselineEnd = requiredDate(userTime.currentDate(command.userId()));
            DebuggerWorkflowUseCase.ExperimentDraft draft = new DebuggerWorkflowUseCase.ExperimentDraft(
                    positiveId(data, INVESTIGATION_ID),
                    positiveId(data, GOAL_ID),
                    requiredText(parts[0], 4_000),
                    baselineEnd.minusDays(baselineDays - 1L),
                    baselineEnd,
                    durationDays,
                    requiredText(parts[1], 200),
                    requiredText(parts[2], 2_000),
                    requiredText(parts[3], 64),
                    direction,
                    meaningfulChange,
                    requiredText(parts[8], 500));
            result = workflow.createExperiment(command.userId(), draft, command.idempotencyKey());
        }

        persistAndRespond(command, Map.of(
                        EXPERIMENT_ID, positive(result.experimentId()),
                        EXPERIMENT_VERSION, nonNegative(result.aggregateVersion()),
                        LAST_UPDATE_ID, command.updateId()),
                TelegramMessages.experimentUpdated(result.experimentId(), result.status()));
    }

    private void handleCheckIn(InboundCommand command) {
        Map<String, Object> data = stateData(command);
        if (alreadyProcessed(command, data)) {
            return;
        }
        String[] parts = parts(command.payload(), 1, 6);
        Long experimentId = positiveId(data, EXPERIMENT_ID);
        DebuggerWorkflowUseCase.CheckInDraft draft = new DebuggerWorkflowUseCase.CheckInDraft(
                experimentId,
                requiredDate(userTime.currentDate(command.userId())),
                requiredText(userTime.getTimeZone(command.userId()), 64),
                enumValue(parts[0], ADHERENCE_VALUES),
                optionalDecimal(part(parts, 1), MAX_EVIDENCE_VALUE),
                optionalRating(part(parts, 2)),
                optionalRating(part(parts, 3)),
                optionalRating(part(parts, 4)),
                optionalText(part(parts, 5), 500));
        DebuggerWorkflowUseCase.EvidenceResult result =
                workflow.recordCheckIn(command.userId(), draft, command.idempotencyKey());

        persistAndRespond(command, Map.of(LAST_UPDATE_ID, command.updateId()),
                TelegramMessages.checkInRecorded(result.recordId(), result.created()));
    }

    private void handleOutcome(InboundCommand command) {
        Map<String, Object> data = stateData(command);
        if (alreadyProcessed(command, data)) {
            return;
        }
        String[] parts = parts(command.payload(), 6, 7);
        DebuggerWorkflowUseCase.OutcomeDraft draft = new DebuggerWorkflowUseCase.OutcomeDraft(
                positiveId(data, EXPERIMENT_ID),
                requiredText(parts[0], 64),
                boundedDecimal(parts[1], MAX_EVIDENCE_VALUE),
                boundedDecimal(parts[2], MAX_EVIDENCE_VALUE),
                requiredText(parts[3], 32),
                positiveInt(parts[4], Integer.MAX_VALUE),
                positiveInt(parts[5], Integer.MAX_VALUE),
                optionalText(part(parts, 6), 1_000));
        DebuggerWorkflowUseCase.EvidenceResult result =
                workflow.recordOutcome(command.userId(), draft, command.idempotencyKey());

        persistAndRespond(command, Map.of(LAST_UPDATE_ID, command.updateId()),
                TelegramMessages.outcomeRecorded(result.recordId(), result.created()));
    }

    private void handleEvaluation(InboundCommand command) {
        if (!command.payload().isBlank()) {
            throw new IllegalArgumentException("evaluation command does not accept payload");
        }
        Map<String, Object> data = stateData(command);
        if (alreadyProcessed(command, data)) {
            return;
        }
        DebuggerWorkflowUseCase.EvaluationResult result = workflow.evaluate(
                command.userId(),
                positiveId(data, EXPERIMENT_ID),
                version(data, EXPERIMENT_VERSION),
                command.idempotencyKey());

        persistAndRespond(command, Map.of(LAST_UPDATE_ID, command.updateId()),
                TelegramMessages.evaluationRecorded(
                        result.evaluationId(), result.recommendedDecision(), result.dataQuality()));
    }

    private boolean alreadyProcessed(InboundCommand command, Map<String, Object> data) {
        Object storedUpdate = data.get(LAST_UPDATE_ID);
        if (storedUpdate instanceof Number number && number.intValue() == command.updateId()) {
            respond(command, TelegramMessages.COMMAND_ALREADY_PROCESSED);
            return true;
        }
        return false;
    }

    private void persistAndRespond(InboundCommand command, Map<String, Object> data, String message) {
        if (states.updateState(command.userId(), command.chatId(), ConversationState.IDLE, data)) {
            respond(command, message);
        }
    }

    private void respond(InboundCommand command, String message) {
        bot.enqueueOwnedMessage(command.userId(), command.chatId(), message);
    }

    private Map<String, Object> stateData(InboundCommand command) {
        Map<String, Object> data = states.getData(command.chatId());
        return data == null ? Map.of() : data;
    }

    private static String[] parts(String payload, int minimum, int maximum) {
        String[] parts = payload.split("\\s*\\|\\s*", -1);
        if (parts.length < minimum || parts.length > maximum) {
            throw new IllegalArgumentException("invalid command field count");
        }
        return parts;
    }

    private static String part(String[] parts, int index) {
        return index < parts.length ? parts[index] : "";
    }

    private static String requiredText(String value, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("required command value is missing");
        }
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException("command value is too long");
        }
        return normalized;
    }

    private static String optionalText(String value, int maximum) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requiredText(value, maximum);
    }

    private static String enumValue(String value, Set<String> accepted) {
        String normalized = requiredText(value, 32).toUpperCase(Locale.ROOT);
        if (!accepted.contains(normalized)) {
            throw new IllegalArgumentException("unsupported command value");
        }
        return normalized;
    }

    private static int positiveInt(String value, int maximum) {
        int parsed = Integer.parseInt(requiredText(value, 10));
        if (parsed < 1 || parsed > maximum) {
            throw new IllegalArgumentException("command number is out of bounds");
        }
        return parsed;
    }

    private static Integer optionalRating(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        int rating = Integer.parseInt(value.trim());
        if (rating < 0 || rating > 10) {
            throw new IllegalArgumentException("rating is out of bounds");
        }
        return rating;
    }

    private static BigDecimal optionalDecimal(String value, BigDecimal maximumAbsolute) {
        return value == null || value.isBlank() ? null : boundedDecimal(value, maximumAbsolute);
    }

    private static BigDecimal positiveDecimal(String value, BigDecimal maximum) {
        BigDecimal parsed = boundedDecimal(value, maximum);
        if (parsed.signum() <= 0) {
            throw new IllegalArgumentException("command number must be positive");
        }
        return parsed;
    }

    private static BigDecimal boundedDecimal(String value, BigDecimal maximumAbsolute) {
        BigDecimal parsed = new BigDecimal(requiredText(value, 64));
        if (parsed.abs().compareTo(maximumAbsolute) > 0) {
            throw new IllegalArgumentException("command number is out of bounds");
        }
        return parsed;
    }

    private static Long positiveId(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("workflow state is missing");
        }
        return positive(number.longValue());
    }

    private static long version(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("workflow version is missing");
        }
        return nonNegative(number.longValue());
    }

    private static Long positive(Long value) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException("workflow identifier is invalid");
        }
        return value;
    }

    private static long nonNegative(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("workflow version is invalid");
        }
        return value;
    }

    private static LocalDate requiredDate(LocalDate value) {
        if (value == null) {
            throw new IllegalArgumentException("current user date is unavailable");
        }
        return value;
    }

}
