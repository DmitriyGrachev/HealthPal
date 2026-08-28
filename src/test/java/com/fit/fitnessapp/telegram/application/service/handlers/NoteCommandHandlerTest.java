package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.api.TelegramNoteRequestedEvent;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NoteCommandHandlerTest {

    private final TelegramBotService bot = mock(TelegramBotService.class);
    private final ConversationStateUseCase states = mock(ConversationStateUseCase.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final NoteCommandHandler handler = new NoteCommandHandler(bot, states, events);

    @Test
    void revokedLinkDuringStateWriteDoesNotQueuePrompt() {
        handler.handle(command(InboundCommand.Type.NOTE, ""));

        verify(states).updateState(30L, 10L, ConversationState.WAITING_NOTE_TYPE);
        verifyNoInteractions(bot);
    }

    @Test
    void storesTypedStepThenPublishesEnumCompatibleNote() {
        when(states.getState(10L)).thenReturn(
                ConversationState.WAITING_NOTE_TYPE,
                ConversationState.WAITING_NOTE_CONTENT);
        when(states.updateState(eq(30L), eq(10L),
                eq(ConversationState.WAITING_NOTE_CONTENT), any())).thenReturn(true);

        handler.handle(command(InboundCommand.Type.TEXT, "Training"));
        when(states.getData(10L)).thenReturn(Map.of("noteType", "TRAINING"));
        handler.handle(command(InboundCommand.Type.TEXT, "Felt strong on squats"));

        ArgumentCaptor<TelegramNoteRequestedEvent> event =
                ArgumentCaptor.forClass(TelegramNoteRequestedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo("TRAINING");
        assertThatCode(() -> UserNoteDto.NoteType.valueOf(event.getValue().type()))
                .doesNotThrowAnyException();
        verify(bot).enqueueOwnedMessage(30L, 10L,
                TelegramMessages.noteSaved("TRAINING", "Felt strong on squats"));
    }

    @Test
    void invalidTypeKeepsStateAndDoesNotPublish() {
        when(states.getState(10L)).thenReturn(ConversationState.WAITING_NOTE_TYPE);

        handler.handle(command(InboundCommand.Type.TEXT, "Sleep"));

        verify(states, never()).updateState(
                eq(30L), eq(10L), eq(ConversationState.WAITING_NOTE_CONTENT), any());
        verify(events, never()).publishEvent(any());
        verify(bot).enqueueOwnedMessage(30L, 10L, TelegramMessages.NOTE_TYPE_INVALID);
    }

    private static InboundCommand command(InboundCommand.Type type, String payload) {
        return new InboundCommand(1, 10L, 20L, 30L, type, payload);
    }
}
