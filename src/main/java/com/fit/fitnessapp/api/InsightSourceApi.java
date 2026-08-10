package com.fit.fitnessapp.api;

import java.time.LocalDate;

public interface InsightSourceApi {

    boolean insightExists(Long userId, InsightType insightType, LocalDate date);

    boolean insightMatchesSnapshot(
            Long userId,
            InsightType insightType,
            LocalDate date,
            String snapshotHash);
}
