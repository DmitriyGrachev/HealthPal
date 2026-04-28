package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.api.InsightType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiInsightRepository extends JpaRepository<AiInsightEntity, Long> {

    Optional<AiInsightEntity> findByUserIdAndDateAndInsightType(
            Long userId,
            LocalDate date,
            InsightType insightType
    );
    List<AiInsightEntity> findTop3ByUserIdOrderByCreatedAtDesc(Long userId);
    @Query("SELECT a FROM AiInsightEntity a WHERE a.userId = :userId " +
            "AND a.insightType = :type ORDER BY a.date DESC LIMIT :limit")
    List<AiInsightEntity> findTopNByUserIdAndInsightTypeOrderByDateDesc(
            @Param("userId") Long userId,
            @Param("type") InsightType type,
            @Param("limit") int limit);
}