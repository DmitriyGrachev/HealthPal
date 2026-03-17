package com.fit.fitnessapp.nutrition.adapter.out.persistence.repository;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatsecretJpaDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FatsecretDayJpaRepository extends JpaRepository<FatsecretJpaDay, Long> {
    Optional<FatsecretJpaDay> findByUserIdAndDate(Long userId, LocalDate date);

    List<FatsecretJpaDay> findByUserIdAndDateBetweenOrderByDate(
            Long userId, LocalDate from, LocalDate to);
    List<FatsecretJpaDay> findByUserIdAndDateIn(Long userId, List<LocalDate> dates);
}
