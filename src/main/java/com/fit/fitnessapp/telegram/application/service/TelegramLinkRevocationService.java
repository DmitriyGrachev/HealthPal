package com.fit.fitnessapp.telegram.application.service;

import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TelegramLinkRevocationService {

    private final TelegramUserRepository telegramUserRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void unlink(Long userId) {
        telegramUserRepository.findByUserIdForUpdate(userId)
                .ifPresent(link -> {
                    jdbcTemplate.update(
                            "DELETE FROM telegram_delivery_outbox WHERE chat_id = ?",
                            link.getChatId());
                    telegramUserRepository.delete(link);
                });
    }
}
