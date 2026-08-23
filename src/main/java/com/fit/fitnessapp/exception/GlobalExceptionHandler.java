package com.fit.fitnessapp.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(ObjectProvider<Clock> clockProvider) {
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        error -> error.getField(),
                        Collectors.mapping(error -> error.getDefaultMessage(), Collectors.toList())));

        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Validation failed",
                request,
                fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> methodValidation(HandlerMethodValidationException exception,
                                                       HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = exception.getParameterValidationResults().stream()
                .collect(Collectors.toMap(
                        result -> result.getMethodParameter().getParameterName() == null
                                ? "parameter" : result.getMethodParameter().getParameterName(),
                        result -> result.getResolvableErrors().stream()
                                .map(error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage())
                                .toList(),
                        (left, right) -> left));
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraintViolation(ConstraintViolationException exception,
                                                          HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = exception.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        violation -> violation.getPropertyPath().toString(),
                        Collectors.mapping(violation -> violation.getMessage(), Collectors.toList())));
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableRequest(HttpMessageNotReadableException exception,
                                                       HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "Malformed request", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> badCredentials(BadCredentialsException exception, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, ErrorCode.BAD_CREDENTIALS, "Wrong login or password", request);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiError> userNotFound(UsernameNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Authentication required", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> forbidden(AccessDeniedException exception, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(DurableJobRetryConflictException.class)
    public ResponseEntity<ApiError> durableJobRetryConflict(
            DurableJobRetryConflictException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ErrorCode.DURABLE_JOB_RETRY_NOT_ALLOWED,
                "Durable job is not eligible for retry", request);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiError> userAlreadyExists(UserAlreadyExistsException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ErrorCode.USER_ALREADY_EXISTS, exception.getMessage(), request);
    }

    @ExceptionHandler(RequiredExternalConnectionMissingException.class)
    public ResponseEntity<ApiError> missingExternalConnection(
            RequiredExternalConnectionMissingException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ErrorCode.FATSECRET_NOT_CONNECTED, exception.getMessage(), request);
    }

    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<ApiError> externalServiceUnavailable(
            ExternalServiceUnavailableException exception,
            HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.AI_UNAVAILABLE,
                "External AI service is temporarily unavailable", request);
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiError> externalApiFailure(RuntimeException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_GATEWAY, ErrorCode.EXTERNAL_API_FAILURE,
                "External provider request failed", request);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> badRequest(Exception exception, HttpServletRequest request) {
        if (isMissingFatSecretConnection(exception)) {
            return error(HttpStatus.CONFLICT, ErrorCode.FATSECRET_NOT_CONNECTED, exception.getMessage(), request);
        }
        return error(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> illegalState(IllegalStateException exception, HttpServletRequest request) {
        if ("User is not authenticated".equals(exception.getMessage())) {
            return error(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Authentication required", request);
        }
        return error(HttpStatus.CONFLICT, ErrorCode.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiError> io(IOException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "Could not read request content", request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> runtime(RuntimeException exception, HttpServletRequest request) {
        if ("Parse error".equals(exception.getMessage())) {
            return error(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "Could not parse uploaded file", request);
        }
        if (isExternalApiFailure(exception)) {
            return error(HttpStatus.BAD_GATEWAY, ErrorCode.EXTERNAL_API_FAILURE,
                    "External provider request failed", request);
        }
        if (exception.getCause() instanceof ExternalServiceUnavailableException) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.AI_UNAVAILABLE, exception.getMessage(), request);
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Internal server error", request);
    }

    private boolean isMissingFatSecretConnection(Exception exception) {
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("not connected to fatsecret");
    }

    private boolean isExternalApiFailure(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("fatsecret")
                && (lowerMessage.contains("failed") || lowerMessage.contains("api error") || lowerMessage.contains("api call"));
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiError.of(code, message, status.value(), pathWithinApplication(request), clock.instant()));
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, List<String>> fieldErrors) {
        return ResponseEntity.status(status)
                .body(ApiError.validation(message, status.value(), pathWithinApplication(request), fieldErrors,
                        clock.instant()));
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        if (contextPath == null || contextPath.isBlank()) {
            return requestUri;
        }
        return requestUri.substring(contextPath.length());
    }
}
