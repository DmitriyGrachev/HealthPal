package com.fit.fitnessapp.exception;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String message) {
      super(message);
    }
}
