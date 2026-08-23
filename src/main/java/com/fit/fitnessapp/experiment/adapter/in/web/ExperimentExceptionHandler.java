package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import com.fit.fitnessapp.experiment.domain.InvalidTransitionException;
import com.fit.fitnessapp.experiment.domain.PrimaryGoalConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {InvestigationController.class, GoalController.class})
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

    private ResponseEntity<ExperimentApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ExperimentApiError(
                code,
                message,
                status.value(),
                pathWithinApplication(request),
                clock.instant(),
                Map.of()));
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
