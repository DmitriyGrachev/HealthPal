package com.fit.fitnessapp.ai.exception;

public class AiRateLimitException extends AiUnavailableException {
    public AiRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
