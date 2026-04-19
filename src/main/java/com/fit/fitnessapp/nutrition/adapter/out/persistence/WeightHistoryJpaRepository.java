package com.fit.fitnessapp.nutrition.adapter.out.persistence;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.WeightHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeightHistoryJpaRepository extends JpaRepository<WeightHistory, Long> {
    List<WeightHistory> findByUserIdOrderByDateDesc(Long userId);
    List<WeightHistory> findByUserIdAndDateBetweenOrderByDateDesc(Long userId, LocalDate from, LocalDate to);
    Optional<WeightHistory> findTopByUserIdOrderByDateDesc(Long userId);
}