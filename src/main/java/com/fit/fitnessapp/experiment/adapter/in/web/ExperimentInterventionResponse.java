package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.Intervention;

public record ExperimentInterventionResponse(String action, String protocol) {
    public static ExperimentInterventionResponse from(Intervention value) {
        return new ExperimentInterventionResponse(value.action(), value.protocol());
    }
}
