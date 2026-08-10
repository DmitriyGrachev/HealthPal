package com.fit.fitnessapp.ai.exception;

public class AiEgressDeniedException extends RuntimeException {

    private final String code;

    public AiEgressDeniedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
