package com.fit.fitnessapp.knowledge.application.port.in;

import com.fit.fitnessapp.knowledge.domain.ClaimConflict;
import com.fit.fitnessapp.knowledge.domain.ClaimDrift;
import java.util.List;

public interface ClaimConflictCommandUseCase {
    ClaimConflict acknowledge(Long userId, Long conflictId, long expectedVersion, String idempotencyKey);
    ClaimConflict dismiss(Long userId, Long conflictId, long expectedVersion, String idempotencyKey);
    List<ClaimDrift> drift(Long userId);
}
