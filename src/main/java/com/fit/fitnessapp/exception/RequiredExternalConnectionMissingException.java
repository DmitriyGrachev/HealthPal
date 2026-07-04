package com.fit.fitnessapp.exception;

public class RequiredExternalConnectionMissingException extends RuntimeException {
    public RequiredExternalConnectionMissingException(String message) {
        super(message);
    }
}
