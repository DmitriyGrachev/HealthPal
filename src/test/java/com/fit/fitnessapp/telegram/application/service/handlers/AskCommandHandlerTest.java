package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.api.AiRateLimitApi;
import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AskCommandHandlerTest {

    @Test
    void publishesQuestionAfterRateLimitAndUsesFencedAcknowledgement() {
        TelegramBotService bot = mock(TelegramBotService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        AiRateLimitApi rateLimit = mock(AiRateLimitApi.class);
        when(rateLimit.tryConsume(42L)).thenReturn(true);
        when(bot.enqueueOwnedMessage(42L, 456L, TelegramMessages.ASK_THINKING)).thenReturn(true);

        new AskCommandHandler(bot, events, rateLimit).handle(command());

        verify(bot).enqueueOwnedMessage(42L, 456L, TelegramMessages.ASK_THINKING);
        ArgumentCaptor<TelegramAskRequestedEvent> event =
                ArgumentCaptor.forClass(TelegramAskRequestedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().question()).isEqualTo("How was my protein today?");
    }

    @Test
    void rateLimitStopsProviderEvent() {
        TelegramBotService bot = mock(TelegramBotService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        AiRateLimitApi rateLimit = mock(AiRateLimitApi.class);

        new AskCommandHandler(bot, events, rateLimit).handle(command());

        verify(events, never()).publishEvent(any());
        verify(bot).enqueueOwnedMessage(42L, 456L, TelegramMessages.ASK_RATE_LIMITED);
    }

    private static InboundCommand command() {
        return new InboundCommand(1, 456L, 123L, 42L, InboundCommand.Type.ASK,
                "How was my protein today?");
    }
}
