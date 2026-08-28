package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartCommandHandler implements CommandHandler {

    private final TelegramBotService bot;

    @Override
    public boolean canHandle(InboundCommand command) {
        return command.type() == InboundCommand.Type.START;
    }

    @Override
    public void handle(InboundCommand command) {
        if (command.userId() == null) {
            bot.sendMessage(command.chatId(), TelegramMessages.startUnlinked());
        } else {
            bot.enqueueOwnedMessage(command.userId(), command.chatId(), TelegramMessages.startLinked());
        }
    }

}
