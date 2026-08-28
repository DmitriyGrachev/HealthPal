package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramLinkCodeManager;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class TestGenerateHandler implements CommandHandler {

    private final TelegramBotService bot;
    private final TelegramLinkCodeManager codes;

    @Override
    public boolean canHandle(InboundCommand command) {
        return command.type() == InboundCommand.Type.TEST_GENERATE;
    }

    @Override
    public void handle(InboundCommand command) {
        String code = codes.generateCode(1L);
        bot.sendMessage(command.chatId(), TelegramMessages.testCodeGenerated(code));
    }

}
