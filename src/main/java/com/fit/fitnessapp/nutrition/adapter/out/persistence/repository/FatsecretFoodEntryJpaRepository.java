package com.fit.fitnessapp.nutrition.adapter.out.persistence.repository;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatsecretFoodEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FatsecretFoodEntryJpaRepository extends JpaRepository<FatsecretFoodEntry, Long> {

    @Modifying
    @Query("DELETE FROM FatsecretFoodEntry e WHERE e.day.id = :dayId")
    void deleteByDayId(@Param("dayId") Long dayId);
}
