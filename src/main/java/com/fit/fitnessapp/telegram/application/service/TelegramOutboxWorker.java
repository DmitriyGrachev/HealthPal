package com.fit.fitnessapp.telegram.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TelegramOutboxWorker {

    private final TelegramBotService telegramBotService;

    @Scheduled(fixedDelay = 30000)
    public void retryFailedDeliveries() {
        telegramBotService.processOutboxRetries();
    }
}
