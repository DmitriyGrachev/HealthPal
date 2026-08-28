package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramLinkBindingService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkCommandHandler implements CommandHandler {

    private final TelegramBotService bot;
    private final TelegramLinkBindingService links;

    @Override
    public boolean canHandle(InboundCommand command) {
        return command.type() == InboundCommand.Type.LINK;
    }

    @Override
    public void handle(InboundCommand command) {
        var result = links.link(command.senderId(), command.chatId(), command.payload());
        switch (result.status()) {
            case LINKED -> bot.enqueueOwnedMessage(
                    result.userId(), command.chatId(), TelegramMessages.LINK_SUCCESS);
            case CODE_REQUIRED -> respond(command, TelegramMessages.LINK_CODE_REQUIRED);
            case INVALID -> respond(command, TelegramMessages.LINK_INVALID);
            case RATE_LIMITED -> respond(command, TelegramMessages.LINK_RATE_LIMITED);
        }
    }

    private void respond(InboundCommand command, String text) {
        if (command.userId() == null) {
            bot.sendMessage(command.chatId(), text);
        } else {
            bot.enqueueOwnedMessage(command.userId(), command.chatId(), text);
        }
    }

}
