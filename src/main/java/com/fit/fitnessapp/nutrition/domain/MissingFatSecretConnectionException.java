package com.fit.fitnessapp.nutrition.domain;

public class MissingFatSecretConnectionException extends RuntimeException {
    public MissingFatSecretConnectionException(Long userId) {
        super("User " + userId + " is not connected to FatSecret");
    }
}
