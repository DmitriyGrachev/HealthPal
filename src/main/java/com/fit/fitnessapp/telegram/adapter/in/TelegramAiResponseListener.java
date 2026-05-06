package com.fit.fitnessapp.telegram.adapter.in;

import com.fit.fitnessapp.telegram.api.TelegramAiResponseEvent;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramAiResponseListener {

    private final TelegramBotService botService;

    @EventListener
    public void onAiResponse(TelegramAiResponseEvent event) {
        log.info("Telegram module received AI Response for user {}", event.userId());
        botService.sendMessage(event.chatId(), event.response());
    }
}
