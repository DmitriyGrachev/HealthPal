package com.fit.fitnessapp.api.evidence;

import java.time.LocalDate;

/** Owner-scoped evidence window; a null subject requests only sources independent of an experiment. */
public record EvidenceSourceRequest(
        Long userId,
        Long subjectId,
        LocalDate fromInclusive,
        LocalDate toInclusive) {

    public EvidenceSourceRequest {
        if (userId == null || userId < 1 || subjectId != null && subjectId < 1) {
            throw new IllegalArgumentException("evidence request identifiers must be positive");
        }
        if (fromInclusive == null || toInclusive == null || toInclusive.isBefore(fromInclusive)) {
            throw new IllegalArgumentException("evidence request window is invalid");
        }
    }
}
