package com.fit.fitnessapp.exception;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.time.Clock;

@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ApiErrorResponseWriter(ObjectMapper objectMapper, ObjectProvider<Clock> clockProvider) {
        this.objectMapper = objectMapper;
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message) throws IOException {
        ApiError error = ApiError.of(code, message, status.value(), pathWithinApplication(request), clock.instant());
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), error);
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
