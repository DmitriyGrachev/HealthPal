package com.fit.fitnessapp.telegram.adapter.in;

import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramNotificationListenerTest {

    @Test
    void insightNotificationOnlyEnqueuesOutboxWork() {
        TelegramBotService botService = mock(TelegramBotService.class);
        TelegramUserRepository users = mock(TelegramUserRepository.class);
        TelegramUserEntity user = new TelegramUserEntity();
        user.setUserId(42L);
        user.setChatId(100L);
        when(users.findByUserId(42L)).thenReturn(Optional.of(user));
        TelegramNotificationListener listener = new TelegramNotificationListener(botService, users);

        listener.onInsightGenerated(new InsightGeneratedEvent(
                42L, LocalDate.of(2026, 8, 9), InsightType.DAILY,
                "Daily insight", "Daily summary", "snapshot"));

        verify(botService).enqueueMessage(100L, TelegramMessages.personalizedInsight("Daily summary"));
    }
}
