package com.fit.fitnessapp.experiment.application.port.in;

import com.fit.fitnessapp.experiment.domain.Experiment;

import java.util.List;
import java.util.Optional;

public interface ExperimentQueryUseCase {
    List<Experiment> findAll(Long userId);

    Optional<Experiment> find(Long userId, Long experimentId);
}
