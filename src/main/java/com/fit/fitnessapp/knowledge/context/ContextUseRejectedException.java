package com.fit.fitnessapp.knowledge.context;

public class ContextUseRejectedException extends RuntimeException {
    public ContextUseRejectedException() {
        super("Context changed or is unavailable; refresh and review it");
    }
}
