package com.fit.fitnessapp.exception;

public final class ErrorCode {
    private ErrorCode() {
    }

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String BAD_CREDENTIALS = "BAD_CREDENTIALS";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String USER_ALREADY_EXISTS = "USER_ALREADY_EXISTS";
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String FATSECRET_NOT_CONNECTED = "FATSECRET_NOT_CONNECTED";
    public static final String AI_UNAVAILABLE = "AI_UNAVAILABLE";
    public static final String EXTERNAL_API_FAILURE = "EXTERNAL_API_FAILURE";
    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String DURABLE_JOB_RETRY_NOT_ALLOWED = "DURABLE_JOB_RETRY_NOT_ALLOWED";
}
