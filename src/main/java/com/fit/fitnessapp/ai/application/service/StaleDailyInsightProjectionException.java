package com.fit.fitnessapp.ai.application.service;

public class StaleDailyInsightProjectionException extends RuntimeException {

    public StaleDailyInsightProjectionException() {
        super("Daily insight source expectation is stale");
    }
}
