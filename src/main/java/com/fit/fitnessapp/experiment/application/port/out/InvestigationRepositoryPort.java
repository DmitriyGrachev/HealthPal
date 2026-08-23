package com.fit.fitnessapp.experiment.application.port.out;

import com.fit.fitnessapp.experiment.domain.Investigation;

import java.util.List;
import java.util.Optional;

public interface InvestigationRepositoryPort {
    Investigation insert(Investigation investigation);
    List<Investigation> findAllByUserId(Long userId);
    Optional<Investigation> findByUserIdAndId(Long userId, Long id);
    boolean updateTransition(Long userId, Long id, long expectedVersion,
                             String status, long nextVersion, java.time.Instant updatedAt);
    void deleteByUserId(Long userId);
    void deleteById(Long userId, Long id);
}
