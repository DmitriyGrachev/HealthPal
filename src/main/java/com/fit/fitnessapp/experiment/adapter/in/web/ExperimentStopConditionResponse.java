package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.StopCondition;

public record ExperimentStopConditionResponse(String code, String description) {
    public static ExperimentStopConditionResponse from(StopCondition value) {
        return new ExperimentStopConditionResponse(value.code(), value.description());
    }
}
