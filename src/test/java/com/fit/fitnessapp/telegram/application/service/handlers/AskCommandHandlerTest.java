package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.api.AiRateLimitApi;
import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AskCommandHandlerTest {

    @Mock private TelegramBotService botService;
    @Mock private TelegramUserRepository telegramUserRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AiRateLimitApi aiRateLimitApi;

    @InjectMocks private AskCommandHandler handler;

    @Test
    void publishesAskEventWhenLinkedUserHasRateLimitToken() {
        Long chatId = 456L;
        Long telegramId = 123L;
        Long appUserId = 42L;
        when(telegramUserRepository.findById(telegramId)).thenReturn(Optional.of(linkedUser(telegramId, appUserId)));
        when(aiRateLimitApi.tryConsume(appUserId)).thenReturn(true);

        handler.handle(update(chatId, telegramId, "/ask How was my protein today?"));

        verify(botService).sendMessage(chatId, TelegramMessages.ASK_THINKING);
        ArgumentCaptor<TelegramAskRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(TelegramAskRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(appUserId);
        assertThat(eventCaptor.getValue().chatId()).isEqualTo(chatId);
        assertThat(eventCaptor.getValue().question()).isEqualTo("How was my protein today?");
    }

    @Test
    void doesNotPublishAskEventWhenRateLimitExhausted() {
        Long chatId = 456L;
        Long telegramId = 123L;
        Long appUserId = 42L;
        when(telegramUserRepository.findById(telegramId)).thenReturn(Optional.of(linkedUser(telegramId, appUserId)));
        when(aiRateLimitApi.tryConsume(appUserId)).thenReturn(false);

        handler.handle(update(chatId, telegramId, "/ask How was my protein today?"));

        verify(aiRateLimitApi).tryConsume(appUserId);
        verify(eventPublisher, never()).publishEvent(any());
        verify(botService, never()).sendMessage(chatId, TelegramMessages.ASK_THINKING);
        verify(botService).sendMessage(chatId, TelegramMessages.ASK_RATE_LIMITED);
    }

    @Test
    void ignoresAskFromChatOtherThanLinkedPrivateChat() {
        Long telegramId = 123L;
        when(telegramUserRepository.findById(telegramId))
                .thenReturn(Optional.of(linkedUser(telegramId, 42L)));

        handler.handle(update(789L, telegramId, "/ask private question"));

        verify(telegramUserRepository).findById(telegramId);
        verifyNoInteractions(botService, eventPublisher, aiRateLimitApi);
    }

    private TelegramUserEntity linkedUser(Long telegramId, Long appUserId) {
        return TelegramUserEntity.builder()
                .telegramId(telegramId)
                .userId(appUserId)
                .chatId(456L)
                .build();
    }

    private Update update(Long chatId, Long telegramId, String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User from = mock(User.class);

        when(update.getMessage()).thenReturn(message);
        when(message.getChat()).thenReturn(chat);
        when(chat.isUserChat()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(telegramId);

        return update;
    }
}
