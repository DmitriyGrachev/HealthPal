package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.api.AiRateLimitApi;
import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AskCommandHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(AskCommandHandler.class);

    private final TelegramBotService bot;
    private final ApplicationEventPublisher events;
    private final AiRateLimitApi rateLimit;

    @Override
    public boolean canHandle(InboundCommand command) {
        return command.type() == InboundCommand.Type.ASK;
    }

    @Override
    public void handle(InboundCommand command) {
        if (command.payload().isBlank()) {
            bot.enqueueOwnedMessage(command.userId(), command.chatId(), TelegramMessages.ASK_REQUIRED);
            return;
        }
        if (!rateLimit.tryConsume(command.userId())) {
            log.info("Telegram AI request rate limited userId={} chatId={}",
                    command.userId(), command.chatId());
            bot.enqueueOwnedMessage(command.userId(), command.chatId(), TelegramMessages.ASK_RATE_LIMITED);
            return;
        }
        if (!bot.enqueueOwnedMessage(
                command.userId(), command.chatId(), TelegramMessages.ASK_THINKING)) {
            return;
        }
        events.publishEvent(new TelegramAskRequestedEvent(
                command.userId(), command.chatId(), command.payload()));
    }
}
