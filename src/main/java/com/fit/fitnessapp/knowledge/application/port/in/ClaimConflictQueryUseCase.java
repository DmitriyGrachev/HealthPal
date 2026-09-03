package com.fit.fitnessapp.knowledge.application.port.in;

import com.fit.fitnessapp.knowledge.domain.ClaimConflict;

import java.util.List;

public interface ClaimConflictQueryUseCase {
    List<ClaimConflict> findOpen(Long userId);
    java.util.Set<Long> conflictedClaimIds(Long userId);
}
