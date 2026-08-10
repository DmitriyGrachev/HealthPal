package com.fit.fitnessapp.telegram.infrastructure.persistence.repository;

import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface TelegramUserRepository extends JpaRepository<TelegramUserEntity, Long> {
    Optional<TelegramUserEntity> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select telegramUser from TelegramUserEntity telegramUser where telegramUser.userId = :userId")
    Optional<TelegramUserEntity> findByUserIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select telegramUser
              from TelegramUserEntity telegramUser
             where telegramUser.userId = :userId
               and telegramUser.chatId = :chatId
            """)
    Optional<TelegramUserEntity> findByUserIdAndChatIdForUpdate(
            @Param("userId") Long userId,
            @Param("chatId") Long chatId);

    void deleteByUserId(Long userId);
}
