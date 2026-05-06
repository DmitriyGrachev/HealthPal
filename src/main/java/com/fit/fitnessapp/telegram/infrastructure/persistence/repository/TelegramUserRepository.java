package com.fit.fitnessapp.telegram.infrastructure.persistence.repository;

import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TelegramUserRepository extends JpaRepository<TelegramUserEntity, Long> {
    Optional<TelegramUserEntity> findByUserId(Long userId);
}
