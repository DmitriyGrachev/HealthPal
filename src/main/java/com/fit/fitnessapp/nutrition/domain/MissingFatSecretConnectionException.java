package com.fit.fitnessapp.nutrition.domain;

import com.fit.fitnessapp.exception.RequiredExternalConnectionMissingException;

public class MissingFatSecretConnectionException extends RequiredExternalConnectionMissingException {
    public MissingFatSecretConnectionException(Long userId) {
        super("User " + userId + " is not connected to FatSecret");
    }
}
