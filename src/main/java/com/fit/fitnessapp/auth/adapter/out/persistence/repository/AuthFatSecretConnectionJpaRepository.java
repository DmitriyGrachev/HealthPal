package com.fit.fitnessapp.auth.adapter.out.persistence.repository;

import com.fit.fitnessapp.auth.adapter.out.persistence.entity.AuthFatSecretConnectionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthFatSecretConnectionJpaRepository extends JpaRepository<AuthFatSecretConnectionJpaEntity, Long> {
    Optional<AuthFatSecretConnectionJpaEntity> findByUserId(Long userId);

}
