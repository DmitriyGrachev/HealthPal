package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.api.TelegramWeightRequestedEvent;
import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WeightCommandHandlerTest {

    private final TelegramBotService bot = mock(TelegramBotService.class);
    private final ConversationStateUseCase states = mock(ConversationStateUseCase.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UserTimeApi userTime = mock(UserTimeApi.class);
    private final WeightCommandHandler handler = new WeightCommandHandler(bot, states, events, userTime);

    @BeforeEach
    void setUp() {
        when(userTime.currentDate(1L)).thenReturn(LocalDate.of(2026, 8, 28));
    }

    @Test
    void startsOnlyWhenCurrentLinkSurvivesStateWrite() {
        when(states.updateState(1L, 456L, ConversationState.WAITING_WEIGHT)).thenReturn(true);

        handler.handle(command(InboundCommand.Type.WEIGHT, ""));

        verify(bot).enqueueOwnedMessage(1L, 456L, TelegramMessages.WEIGHT_PROMPT);
    }

    @Test
    void recordsNormalizedWeightFromWaitingState() {
        when(states.getState(456L)).thenReturn(ConversationState.WAITING_WEIGHT);

        handler.handle(command(InboundCommand.Type.TEXT, "82,5"));

        ArgumentCaptor<TelegramWeightRequestedEvent> event =
                ArgumentCaptor.forClass(TelegramWeightRequestedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().weightKg()).isEqualByComparingTo("82.5");
        verify(bot).enqueueOwnedMessage(1L, 456L, TelegramMessages.weightRecorded("82.5"));
        verify(states).clearState(456L);
    }

    @Test
    void invalidWeightDoesNotPublish() {
        when(states.getState(456L)).thenReturn(ConversationState.WAITING_WEIGHT);

        handler.handle(command(InboundCommand.Type.TEXT, "invalid"));

        verify(events, never()).publishEvent(any());
        verify(bot).enqueueOwnedMessage(1L, 456L, TelegramMessages.WEIGHT_INVALID_FORMAT);
    }

    private static InboundCommand command(InboundCommand.Type type, String payload) {
        return new InboundCommand(1, 456L, 123L, 1L, type, payload);
    }
}
