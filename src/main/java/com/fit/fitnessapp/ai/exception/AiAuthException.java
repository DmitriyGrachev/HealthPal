package com.fit.fitnessapp.ai.exception;

import com.fit.fitnessapp.exception.ExternalApiException;

public class AiAuthException extends ExternalApiException {
    public AiAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
