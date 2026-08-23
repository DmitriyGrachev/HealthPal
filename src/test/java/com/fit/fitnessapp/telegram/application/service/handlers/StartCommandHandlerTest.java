package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartCommandHandlerTest {

    @Mock
    private TelegramBotService botService;
    @Mock
    private TelegramUserRepository telegramUserRepository;

    private StartCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StartCommandHandler(botService, telegramUserRepository);
    }

    @Test
    void ignoresNonPrivateChatBeforeRepositoryOrBotInteractions() {
        handler.handle(nonPrivateStartUpdate());

        verifyNoInteractions(telegramUserRepository, botService);
    }

    @Test
    void ignoresUpdateWithoutChatBeforeRepositoryOrBotInteractions() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.getMessage()).thenReturn(message);
        when(message.getChat()).thenReturn(null);

        handler.handle(update);

        verifyNoInteractions(telegramUserRepository, botService);
    }

    private Update nonPrivateStartUpdate() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User from = mock(User.class);

        lenient().when(update.getMessage()).thenReturn(message);
        lenient().when(message.getChat()).thenReturn(chat);
        lenient().when(chat.isUserChat()).thenReturn(false);
        lenient().when(message.getChatId()).thenReturn(456L);
        lenient().when(message.getFrom()).thenReturn(from);
        lenient().when(from.getId()).thenReturn(123L);

        return update;
    }
}
