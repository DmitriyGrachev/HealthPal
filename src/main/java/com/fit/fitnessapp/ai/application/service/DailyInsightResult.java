package com.fit.fitnessapp.ai.application.service;

public record DailyInsightResult(Status status, String errorCode) {

    public enum Status {
        GENERATED,
        PUBLISHED_EXISTING,
        SKIPPED_FRESH,
        NO_SNAPSHOT,
        AI_FAILED
    }

    public static DailyInsightResult generated() {
        return new DailyInsightResult(Status.GENERATED, null);
    }

    public static DailyInsightResult publishedExisting() {
        return new DailyInsightResult(Status.PUBLISHED_EXISTING, null);
    }

    public static DailyInsightResult skippedFresh() {
        return new DailyInsightResult(Status.SKIPPED_FRESH, null);
    }

    public static DailyInsightResult noSnapshot() {
        return new DailyInsightResult(Status.NO_SNAPSHOT, null);
    }

    public static DailyInsightResult aiFailed(String errorCode) {
        return new DailyInsightResult(Status.AI_FAILED, errorCode);
    }
}
