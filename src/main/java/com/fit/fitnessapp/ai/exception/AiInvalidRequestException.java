package com.fit.fitnessapp.ai.exception;

public class AiInvalidRequestException extends RuntimeException {
    public AiInvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
