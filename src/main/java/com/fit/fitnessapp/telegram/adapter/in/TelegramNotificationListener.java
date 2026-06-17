package com.fit.fitnessapp.telegram.adapter.in;

import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramNotificationListener {

    private final TelegramBotService botService;
    private final TelegramUserRepository telegramUserRepository;

    @ApplicationModuleListener
    public void onInsightGenerated(InsightGeneratedEvent event) {
        log.info("Telegram module received InsightGeneratedEvent for user {}", event.userId());

        Optional<TelegramUserEntity> userOpt = telegramUserRepository.findByUserId(event.userId());

        userOpt.ifPresent(telegramUser -> {
            String message = TelegramMessages.insight(
                    event.insightType(),
                    event.date(),
                    event.content());

            if (event.telegramSummary() != null && !event.telegramSummary().isBlank()) {
                message = TelegramMessages.personalizedInsight(event.telegramSummary());
            }

            botService.sendMessage(telegramUser.getChatId(), message);
        });
    }
}
