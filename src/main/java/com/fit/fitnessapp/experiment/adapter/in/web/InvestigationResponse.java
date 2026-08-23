package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.Investigation;
import com.fit.fitnessapp.experiment.domain.InvestigationStatus;

import java.time.Instant;

public record InvestigationResponse(Long id, Long userId, String title, String problemStatement,
                                    InvestigationStatus status, long aggregateVersion,
                                    Instant createdAt, Instant updatedAt) {
    public static InvestigationResponse from(Investigation value) {
        return new InvestigationResponse(value.id(), value.userId(), value.title(), value.problemStatement(),
                value.status(), value.aggregateVersion(), value.createdAt(), value.updatedAt());
    }
}
