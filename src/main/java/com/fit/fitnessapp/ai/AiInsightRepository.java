package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.api.InsightType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface AiInsightRepository extends JpaRepository<AiInsightEntity, Long> {

    Optional<AiInsightEntity> findByUserIdAndDateAndInsightType(
            Long userId,
            LocalDate date,
            InsightType insightType
    );
}