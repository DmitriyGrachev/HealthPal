package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.api.TelegramTodayRequestedEvent;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TodayCommandHandlerTest {

    private final TelegramBotService botService = mock(TelegramBotService.class);
    private final TelegramUserRepository telegramUserRepository = mock(TelegramUserRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final UserTimeApi userTimeApi = mock(UserTimeApi.class);
    private final TodayCommandHandler handler = new TodayCommandHandler(
            botService, telegramUserRepository, eventPublisher, userTimeApi);

    @BeforeEach
    void resetMocks() {
        org.mockito.Mockito.reset(botService, telegramUserRepository, eventPublisher, userTimeApi);
        when(userTimeApi.currentDate(42L)).thenReturn(java.time.LocalDate.now());
    }

    @Test
    void sendsGeneratingMessageAndPublishesTodayEventForLinkedPrivateChat() {
        Long chatId = 456L;
        Long telegramId = 123L;
        Long userId = 42L;
        when(telegramUserRepository.findById(telegramId)).thenReturn(Optional.of(
                TelegramUserEntity.builder().telegramId(telegramId).userId(userId).chatId(chatId).build()));

        handler.handle(update(chatId, telegramId, "/today"));

        verify(botService).sendMessage(chatId, TelegramMessages.TODAY_GENERATING);
        ArgumentCaptor<TelegramTodayRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(TelegramTodayRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(userId);
        assertThat(eventCaptor.getValue().chatId()).isEqualTo(chatId);
        assertThat(eventCaptor.getValue().date()).isEqualTo(java.time.LocalDate.now());
    }

    @Test
    void ignoresTodayFromChatOtherThanLinkedPrivateChat() {
        Long telegramId = 123L;
        when(telegramUserRepository.findById(telegramId)).thenReturn(Optional.of(
                TelegramUserEntity.builder().telegramId(telegramId).userId(42L).chatId(456L).build()));

        handler.handle(update(789L, telegramId, "/today"));

        verify(telegramUserRepository).findById(telegramId);
        verifyNoInteractions(botService, eventPublisher);
    }

    private Update update(Long chatId, Long telegramId, String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User from = mock(User.class);
        when(update.getMessage()).thenReturn(message);
        when(message.getChat()).thenReturn(chat);
        when(chat.isUserChat()).thenReturn(true);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getText()).thenReturn(text);
        when(message.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(telegramId);
        return update;
    }
}
