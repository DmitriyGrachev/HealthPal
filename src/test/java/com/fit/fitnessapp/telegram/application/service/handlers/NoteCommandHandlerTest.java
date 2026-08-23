package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.auth.domain.UserNoteDto;
import com.fit.fitnessapp.api.TelegramNoteRequestedEvent;
import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NoteCommandHandlerTest {

    private final TelegramBotService botService = mock(TelegramBotService.class);
    private final TelegramUserRepository telegramUserRepository = mock(TelegramUserRepository.class);
    private final ConversationStateUseCase stateUseCase = mock(ConversationStateUseCase.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final NoteCommandHandler handler = new NoteCommandHandler(
            botService,
            telegramUserRepository,
            stateUseCase,
            eventPublisher
    );

    @Test
    void revokedLinkDuringNoteStartDoesNotSendPrompt() {
        Long chatId = 10L;
        Long telegramId = 20L;
        Long appUserId = 30L;
        when(telegramUserRepository.findById(telegramId)).thenReturn(Optional.of(
                TelegramUserEntity.builder()
                        .telegramId(telegramId).userId(appUserId).chatId(chatId).build()));
        when(stateUseCase.getState(chatId)).thenReturn(ConversationState.IDLE);
        when(stateUseCase.updateState(appUserId, chatId, ConversationState.WAITING_NOTE_TYPE))
                .thenReturn(false);

        handler.handle(update(chatId, telegramId, "/note"));

        verifyNoInteractions(botService);
    }

    @Test
    void visibleTrainingNoteTypePublishesEnumCompatibleEventType() {
        Long chatId = 10L;
        Long telegramId = 20L;
        Long appUserId = 30L;

        TelegramUserEntity linkedUser = TelegramUserEntity.builder()
                .telegramId(telegramId)
                .userId(appUserId)
                .chatId(chatId)
                .build();
        when(telegramUserRepository.findById(telegramId)).thenReturn(Optional.of(linkedUser));

        when(stateUseCase.getState(chatId)).thenReturn(
                ConversationState.WAITING_NOTE_TYPE,
                ConversationState.WAITING_NOTE_CONTENT
        );
        when(stateUseCase.updateState(
                eq(appUserId),
                eq(chatId),
                eq(ConversationState.WAITING_NOTE_CONTENT),
                any())).thenReturn(true);

        handler.handle(update(chatId, telegramId, "Training"));

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(stateUseCase).updateState(
                eq(appUserId),
                eq(chatId),
                eq(ConversationState.WAITING_NOTE_CONTENT),
                dataCaptor.capture()
        );

        String storedType = (String) dataCaptor.getValue().get("noteType");
        when(stateUseCase.getData(chatId)).thenReturn(Map.of("noteType", storedType));

        handler.handle(update(chatId, telegramId, "Felt strong on squats"));

        ArgumentCaptor<TelegramNoteRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(TelegramNoteRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        String eventType = eventCaptor.getValue().type();
        assertThat(eventType).isEqualTo(storedType);
        assertThatCode(() -> UserNoteDto.NoteType.valueOf(eventType));
        verify(botService).enqueueOwnedMessage(
                appUserId, chatId, com.fit.fitnessapp.telegram.application.service.TelegramMessages.noteSaved(
                        storedType, "Felt strong on squats"));
        verify(botService, never()).sendMessage(
                chatId, com.fit.fitnessapp.telegram.application.service.TelegramMessages.noteSaved(
                        storedType, "Felt strong on squats"));
    }

    @Test
    void invalidNoteTypeKeepsWaitingForTypeAndDoesNotPublishNote() {
        Long chatId = 10L;
        Long telegramId = 20L;
        Long appUserId = 30L;

        TelegramUserEntity linkedUser = TelegramUserEntity.builder()
                .telegramId(telegramId)
                .userId(appUserId)
                .chatId(chatId)
                .build();
        when(telegramUserRepository.findById(telegramId)).thenReturn(Optional.of(linkedUser));
        when(stateUseCase.getState(chatId)).thenReturn(ConversationState.WAITING_NOTE_TYPE);

        handler.handle(update(chatId, telegramId, "Sleep"));

        verify(stateUseCase, never()).updateState(
                eq(appUserId), eq(chatId), eq(ConversationState.WAITING_NOTE_CONTENT), any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(botService).sendMessage(chatId, com.fit.fitnessapp.telegram.application.service.TelegramMessages.NOTE_TYPE_INVALID);
    }

    @Test
    void ignoresNoteFromChatOtherThanLinkedPrivateChatWithoutReadingState() {
        Long telegramId = 20L;
        when(telegramUserRepository.findById(telegramId)).thenReturn(Optional.of(
                TelegramUserEntity.builder().telegramId(telegramId).userId(30L).chatId(456L).build()));

        handler.handle(update(789L, telegramId, "/note"));

        verify(telegramUserRepository).findById(telegramId);
        verifyNoInteractions(botService, stateUseCase, eventPublisher);
    }

    @Test
    void canHandleMismatchedSenderWithoutReadingState() {
        Long telegramId = 20L;
        when(telegramUserRepository.findById(telegramId)).thenReturn(Optional.of(
                TelegramUserEntity.builder().telegramId(telegramId).userId(30L).chatId(456L).build()));

        assertThat(handler.canHandle(update(789L, telegramId, "private note"))).isFalse();

        verify(telegramUserRepository).findById(telegramId);
        verifyNoInteractions(stateUseCase);
    }

    private Update update(Long chatId, Long telegramId, String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User from = mock(User.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.getChat()).thenReturn(chat);
        when(chat.isUserChat()).thenReturn(true);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(telegramId);

        return update;
    }

    private static void assertThatCode(Runnable runnable) {
        org.assertj.core.api.Assertions.assertThatCode(runnable::run).doesNotThrowAnyException();
    }
}
