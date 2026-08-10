package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.ai.application.port.out.AiInsightPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiInsightRepository extends JpaRepository<AiInsightEntity, Long>, AiInsightPort {

    Optional<AiInsightEntity> findByUserIdAndDateAndInsightType(
            Long userId,
            LocalDate date,
            InsightType insightType
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT insight FROM AiInsightEntity insight WHERE insight.userId = :userId " +
            "AND insight.date = :date AND insight.insightType = :type")
    Optional<AiInsightEntity> findSourceForUpdate(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("type") InsightType insightType);

    List<AiInsightEntity> findTop3ByUserIdOrderByCreatedAtDesc(Long userId);
    @Query("SELECT a FROM AiInsightEntity a WHERE a.userId = :userId " +
            "AND a.insightType = :type ORDER BY a.date DESC LIMIT :limit")
    List<AiInsightEntity> findTopNByUserIdAndInsightTypeOrderByDateDesc(
            @Param("userId") Long userId,
            @Param("type") InsightType type,
            @Param("limit") int limit);
}
