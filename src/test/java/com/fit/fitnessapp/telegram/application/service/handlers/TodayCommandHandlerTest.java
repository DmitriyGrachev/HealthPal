package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TodayCommandHandlerTest {

    private final TelegramBotService botService = mock(TelegramBotService.class);
    private final TelegramUserRepository telegramUserRepository = mock(TelegramUserRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final TodayCommandHandler handler = new TodayCommandHandler(
            botService, telegramUserRepository, eventPublisher);

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
        User from = mock(User.class);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getText()).thenReturn(text);
        when(message.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(telegramId);
        return update;
    }
}
