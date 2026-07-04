package com.fit.fitnessapp.ai.exception;

import com.fit.fitnessapp.exception.ExternalServiceUnavailableException;

public class AiUnavailableException extends ExternalServiceUnavailableException {
    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
