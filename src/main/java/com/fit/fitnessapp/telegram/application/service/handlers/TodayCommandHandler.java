package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.api.TelegramTodayRequestedEvent;
import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TodayCommandHandler implements CommandHandler {

    private final TelegramBotService bot;
    private final ApplicationEventPublisher events;
    private final UserTimeApi userTime;

    @Override
    public boolean canHandle(InboundCommand command) {
        return command.type() == InboundCommand.Type.TODAY;
    }

    @Override
    public void handle(InboundCommand command) {
        if (!bot.enqueueOwnedMessage(
                command.userId(), command.chatId(), TelegramMessages.TODAY_GENERATING)) {
            return;
        }
        events.publishEvent(new TelegramTodayRequestedEvent(
                command.userId(), command.chatId(), userTime.currentDate(command.userId())));
    }
}
