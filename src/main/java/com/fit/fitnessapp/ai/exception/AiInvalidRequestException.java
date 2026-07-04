package com.fit.fitnessapp.ai.exception;

import com.fit.fitnessapp.exception.ExternalApiException;

public class AiInvalidRequestException extends ExternalApiException {
    public AiInvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
