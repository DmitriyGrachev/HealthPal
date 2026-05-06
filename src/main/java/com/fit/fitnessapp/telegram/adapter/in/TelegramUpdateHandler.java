package com.fit.fitnessapp.telegram.adapter.in;

import com.fit.fitnessapp.telegram.application.service.handlers.CommandHandler;
import com.fit.fitnessapp.telegram.infrastructure.config.TelegramProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
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
        log.debug("Update received: {}", update);
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            log.info("Message from {}: {}", update.getMessage().getChatId(), text);
            for (CommandHandler handler : handlers) {
                if (handler.canHandle(update)) {
                    handler.handle(update);
                    return;
                }
            }
            
            // Default response if no handler matched
            log.info("No handler found for message: {}", update.getMessage().getText());
        }
    }
}
