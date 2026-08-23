package com.fit.fitnessapp.experiment.application.port.in;

import com.fit.fitnessapp.experiment.domain.Investigation;

import java.util.List;
import java.util.Optional;
import org.springframework.modulith.NamedInterface;

@NamedInterface("query-api")
public interface InvestigationQueryUseCase {
    List<Investigation> findAll(Long userId);
    Optional<Investigation> find(Long userId, Long investigationId);
}
