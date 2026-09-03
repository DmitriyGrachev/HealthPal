package com.fit.fitnessapp.knowledge.adapter.in.web;

import com.fit.fitnessapp.knowledge.application.service.KnowledgeClaimIdempotencyConflictException;
import com.fit.fitnessapp.knowledge.application.service.KnowledgeClaimNotFoundException;
import com.fit.fitnessapp.knowledge.application.service.KnowledgeClaimOwnerNotFoundException;
import com.fit.fitnessapp.knowledge.application.service.KnowledgeClaimVersionConflictException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {KnowledgeClaimController.class, ClaimConflictController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class KnowledgeExceptionHandler {
    @ExceptionHandler({KnowledgeClaimNotFoundException.class, KnowledgeClaimOwnerNotFoundException.class})
    ResponseEntity<KnowledgeApiError> notFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request);
    }

    @ExceptionHandler(KnowledgeClaimVersionConflictException.class)
    ResponseEntity<KnowledgeApiError> versionConflict(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Claim version conflict", request);
    }

    @ExceptionHandler(KnowledgeClaimIdempotencyConflictException.class)
    ResponseEntity<KnowledgeApiError> idempotencyConflict(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key conflicts with an earlier command", request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingRequestValueException.class, IllegalArgumentException.class})
    ResponseEntity<KnowledgeApiError> validation(HttpServletRequest request) {
        // Never echo rejected claim content, parser messages, or values from validation exceptions.
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", request);
    }

    private ResponseEntity<KnowledgeApiError> error(HttpStatus status, String code, String message,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(status).body(new KnowledgeApiError(code, message, status.value(),
                request.getRequestURI().substring(request.getContextPath().length()), Instant.now(), Map.of()));
    }

    record KnowledgeApiError(String code, String message, int status, String path,
                             Instant timestamp, Map<String, Object> fieldErrors) { }
}
