package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.ExperimentNotCompletedException;
import com.fit.fitnessapp.experiment.domain.ExperimentInFlightConflictException;
import com.fit.fitnessapp.experiment.domain.CheckInDateConflictException;
import com.fit.fitnessapp.experiment.domain.DecisionAlreadyRecordedException;
import com.fit.fitnessapp.experiment.domain.EvaluationAlreadyExistsException;
import com.fit.fitnessapp.experiment.domain.EvaluationInsufficientEvidenceException;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import com.fit.fitnessapp.experiment.domain.InvalidTransitionException;
import com.fit.fitnessapp.experiment.domain.OutcomeAlreadyRecordedException;
import com.fit.fitnessapp.experiment.domain.PrimaryGoalConflictException;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice(assignableTypes = {
        InvestigationController.class,
        GoalController.class,
        ExperimentController.class,
        ExperimentEvidenceController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExperimentExceptionHandler {

    private final Clock clock;

    public ExperimentExceptionHandler(ObjectProvider<Clock> clockProvider) {
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
    }

    @ExceptionHandler(ExperimentNotFoundException.class)
    ResponseEntity<ExperimentApiError> notFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request);
    }

    @ExceptionHandler(AggregateVersionConflictException.class)
    ResponseEntity<ExperimentApiError> versionConflict(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Aggregate version conflict", request);
    }

    @ExceptionHandler(PrimaryGoalConflictException.class)
    ResponseEntity<ExperimentApiError> primaryGoalConflict(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "PRIMARY_GOAL_CONFLICT",
                "Primary active goal already exists", request);
    }

    @ExceptionHandler(InvalidTransitionException.class)
    ResponseEntity<ExperimentApiError> invalidTransition(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Invalid state transition", request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ExperimentApiError> idempotencyConflict(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                "Idempotency key is already bound to a different command", request);
    }

    @ExceptionHandler(ExperimentInFlightConflictException.class)
    ResponseEntity<ExperimentApiError> inFlightConflict(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "EXPERIMENT_IN_FLIGHT_CONFLICT",
                "Another Experiment is already in flight", request);
    }

    @ExceptionHandler(CheckInDateConflictException.class)
    ResponseEntity<ExperimentApiError> checkInDateConflict(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CHECK_IN_DATE_CONFLICT",
                "A check-in already exists for this local date", request);
    }

    @ExceptionHandler(OutcomeAlreadyRecordedException.class)
    ResponseEntity<ExperimentApiError> outcomeAlreadyRecorded(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "OUTCOME_ALREADY_RECORDED",
                "The primary Outcome has already been recorded", request);
    }

    @ExceptionHandler(EvaluationAlreadyExistsException.class)
    ResponseEntity<ExperimentApiError> evaluationAlreadyExists(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "EVALUATION_ALREADY_EXISTS",
                "The Evaluation already exists", request);
    }

    @ExceptionHandler(DecisionAlreadyRecordedException.class)
    ResponseEntity<ExperimentApiError> decisionAlreadyRecorded(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DECISION_ALREADY_RECORDED",
                "The Decision has already been recorded", request);
    }

    @ExceptionHandler(ExperimentNotCompletedException.class)
    ResponseEntity<ExperimentApiError> experimentNotCompleted(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "EXPERIMENT_NOT_COMPLETED",
                "The Experiment is not completed", request);
    }

    @ExceptionHandler(com.fit.fitnessapp.experiment.api.DecisionContextRejectedException.class)
    ResponseEntity<ExperimentApiError> decisionContextRejected(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DECISION_CONTEXT_REJECTED",
                "Decision context is unavailable, stale, unconfirmed or conflicting; refresh and review it", request);
    }

    @ExceptionHandler(EvaluationInsufficientEvidenceException.class)
    ResponseEntity<ExperimentApiError> evaluationInsufficientEvidence(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "EVALUATION_INSUFFICIENT_EVIDENCE",
                "The Experiment does not have sufficient evidence for evaluation", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ExperimentApiError> validation(MethodArgumentNotValidException exception,
                                                    HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        fieldError -> fieldError.getField(),
                        Collectors.mapping(fieldError -> fieldError.getDefaultMessage() == null
                                ? "Invalid value" : fieldError.getDefaultMessage(), Collectors.toList())));
        return validationError(request, fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ExperimentApiError> methodValidation(HandlerMethodValidationException exception,
                                                          HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = exception.getParameterValidationResults().stream()
                .collect(Collectors.toMap(
                        result -> result.getMethodParameter().getParameterName() == null
                                ? "parameter" : result.getMethodParameter().getParameterName(),
                        result -> result.getResolvableErrors().stream()
                                .map(error -> error.getDefaultMessage() == null
                                        ? "Invalid value" : error.getDefaultMessage())
                                .toList(),
                        (left, right) -> left));
        return validationError(request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ExperimentApiError> constraintViolation(ConstraintViolationException exception,
                                                             HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = exception.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        violation -> violation.getPropertyPath().toString(),
                        Collectors.mapping(violation -> violation.getMessage() == null
                                ? "Invalid value" : violation.getMessage(), Collectors.toList())));
        return validationError(request, fieldErrors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ResponseEntity<ExperimentApiError> malformedRequest(Exception exception, HttpServletRequest request) {
        if (!isExperimentRequest(request)) {
            return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed request", request);
        }
        return validationError(request, Map.of());
    }

    private static boolean isExperimentRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath == null || contextPath.isBlank()
                ? uri : uri.substring(contextPath.length());
        return path.equals("/api/v1/experiments") || path.startsWith("/api/v1/experiments/");
    }

    private ResponseEntity<ExperimentApiError> validationError(
            HttpServletRequest request, Map<String, List<String>> fieldErrors) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", request, fieldErrors);
    }

    private ResponseEntity<ExperimentApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        return error(status, code, message, request, Map.of());
    }

    private ResponseEntity<ExperimentApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, List<String>> fieldErrors) {
        return ResponseEntity.status(status).body(new ExperimentApiError(
                code,
                message,
                status.value(),
                pathWithinApplication(request),
                clock.instant(),
                fieldErrors));
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        return contextPath == null || contextPath.isBlank()
                ? requestUri
                : requestUri.substring(contextPath.length());
    }

    record ExperimentApiError(
            String code,
            String message,
            int status,
            String path,
            Instant timestamp,
            Map<String, List<String>> fieldErrors) {
    }
}
