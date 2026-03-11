package com.fit.fitnessapp.nutrition.adapter.out.persistence.repository;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatSecretConnectionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FatSecretConnectionJpaRepository extends JpaRepository<FatSecretConnectionJpaEntity, Long> {
    Optional<FatSecretConnectionJpaEntity> findByUserId(Long userId);

}