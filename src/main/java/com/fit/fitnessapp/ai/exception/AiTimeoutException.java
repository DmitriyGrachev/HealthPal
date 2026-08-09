package com.fit.fitnessapp.ai.exception;

public class AiTimeoutException extends AiUnavailableException {
    public AiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
