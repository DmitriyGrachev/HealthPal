package com.fit.fitnessapp.telegram.adapter.in;

import com.fit.fitnessapp.telegram.application.service.handlers.CommandHandler;
import com.fit.fitnessapp.telegram.infrastructure.config.TelegramProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Slf4j
@Component
@org.springframework.context.annotation.Primary
public class TelegramUpdateHandler extends TelegramLongPollingBot {

    private final TelegramProperties properties;
    private final List<CommandHandler> handlers;

    public TelegramUpdateHandler(TelegramProperties properties, List<CommandHandler> handlers) {
        super(properties.getToken());
        this.properties = properties;
        this.handlers = handlers;
        log.info("🤖 Telegram Bot Handler initialized for user: {}", properties.getUsername());
    }

    @Override
    public String getBotUsername() {
        return properties.getUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) {
            log.debug("Telegram update ignored status=no_message");
            return;
        }

        Message message = update.getMessage();
        Long chatId = message.getChatId();
        if (!message.hasText()) {
            log.debug("Telegram update ignored chatId={} status=no_text", chatId);
            return;
        }

        for (CommandHandler handler : handlers) {
            if (handler.canHandle(update)) {
                log.info("Telegram message handled chatId={} command={} handler={} status=handled",
                        chatId, handler.getCommand(), handler.getClass().getSimpleName());
                handler.handle(update);
                return;
            }
        }

        log.info("Telegram message unhandled chatId={} status=unhandled", chatId);
    }
}
