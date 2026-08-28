package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.api.TelegramTodayRequestedEvent;
import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TodayCommandHandlerTest {

    @Test
    void queuesGeneratingMessageAndPublishesOwnerScopedDate() {
        TelegramBotService bot = mock(TelegramBotService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        UserTimeApi userTime = mock(UserTimeApi.class);
        when(userTime.currentDate(42L)).thenReturn(LocalDate.of(2026, 8, 28));
        when(bot.enqueueOwnedMessage(42L, 456L, TelegramMessages.TODAY_GENERATING)).thenReturn(true);

        new TodayCommandHandler(bot, events, userTime).handle(new InboundCommand(
                1, 456L, 123L, 42L, InboundCommand.Type.TODAY, ""));

        verify(bot).enqueueOwnedMessage(42L, 456L, TelegramMessages.TODAY_GENERATING);
        ArgumentCaptor<TelegramTodayRequestedEvent> event =
                ArgumentCaptor.forClass(TelegramTodayRequestedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().date()).isEqualTo(LocalDate.of(2026, 8, 28));
    }
}
