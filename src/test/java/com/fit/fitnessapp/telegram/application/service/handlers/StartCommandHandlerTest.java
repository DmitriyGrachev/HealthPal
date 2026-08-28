package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StartCommandHandlerTest {

    @Test
    void usesFencedDeliveryForLinkedOwner() {
        TelegramBotService bot = mock(TelegramBotService.class);
        new StartCommandHandler(bot).handle(command(42L));

        verify(bot).enqueueOwnedMessage(42L, 456L, TelegramMessages.startLinked());
    }

    private static InboundCommand command(Long userId) {
        return new InboundCommand(1, 456L, 123L, userId, InboundCommand.Type.START, "");
    }
}
