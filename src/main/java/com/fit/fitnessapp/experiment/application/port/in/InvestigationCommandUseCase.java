package com.fit.fitnessapp.experiment.application.port.in;

import com.fit.fitnessapp.experiment.domain.Investigation;

import org.springframework.modulith.NamedInterface;

@NamedInterface("command-api")
public interface InvestigationCommandUseCase {
    Investigation create(Long userId, String title, String problemStatement, String idempotencyKey);
    Investigation transition(Long userId, Long investigationId, String command,
                             long expectedVersion, String idempotencyKey, String reason);
    void deleteByOwner(Long userId);
}
