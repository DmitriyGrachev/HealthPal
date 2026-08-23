package com.fit.fitnessapp.telegram.adapter.in;

import com.fit.fitnessapp.telegram.application.service.handlers.CommandHandler;
import com.fit.fitnessapp.telegram.infrastructure.config.TelegramProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;

@Slf4j
@Component
public class TelegramUpdateHandler implements LongPollingSingleThreadUpdateConsumer {

    private final List<CommandHandler> handlers;

    public TelegramUpdateHandler(TelegramProperties properties, List<CommandHandler> handlers) {
        this.handlers = handlers;
        log.info("Telegram Bot Handler initialized username={}", properties.getUsername());
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage()) {
            log.debug("Telegram update ignored status=no_message");
            return;
        }

        Message message = update.getMessage();
        if (message.getChat() == null || !Boolean.TRUE.equals(message.getChat().isUserChat())) {
            log.debug("Telegram update ignored status=non_private_chat");
            return;
        }
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
