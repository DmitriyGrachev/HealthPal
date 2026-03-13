package com.fit.fitnessapp.nutrition.adapter.out.persistence.repository;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatsecretJpaDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface FatsecretDayJpaRepository extends JpaRepository<FatsecretJpaDay, Long> {
    Optional<FatsecretJpaDay> findByUserIdAndDate(Long userId, LocalDate date);
}
