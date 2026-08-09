package com.fit.fitnessapp.ai.application.port.out;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.api.InsightType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence contract used by AI application workflows. */
public interface AiInsightPort {

    Optional<AiInsightEntity> findByUserIdAndDateAndInsightType(Long userId, LocalDate date, InsightType type);

    List<AiInsightEntity> findTop3ByUserIdOrderByCreatedAtDesc(Long userId);

    List<AiInsightEntity> findTopNByUserIdAndInsightTypeOrderByDateDesc(Long userId, InsightType type, int limit);

}
