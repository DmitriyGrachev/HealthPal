package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramLinkCodeManager;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkCommandHandlerTest {

    @Mock
    private TelegramBotService botService;
    @Mock
    private TelegramUserRepository telegramUserRepository;

    private TelegramLinkCodeManager codeManager;
    private LinkCommandHandler handler;

    @BeforeEach
    void setUp() {
        codeManager = spy(new TelegramLinkCodeManager());
        handler = new LinkCommandHandler(botService, codeManager, telegramUserRepository);
    }

    @Test
    void stopsCheckingCodesAfterRepeatedInvalidAttemptsForChat() {
        Long chatId = 456L;
        Long telegramId = 123L;

        for (int attempt = 0; attempt < 5; attempt++) {
            handler.handle(update(chatId, telegramId, "/link 00000" + attempt));
        }

        clearInvocations(botService, codeManager, telegramUserRepository);
        handler.handle(update(chatId, telegramId, "/link 999999"));

        verify(codeManager, never()).getUserIdByCode(anyString());
        verify(telegramUserRepository, never()).save(any());
        verify(botService).sendMessage(eq(chatId), eq(TelegramMessages.LINK_RATE_LIMITED));
    }

    @Test
    void successfulLinkInvalidatesCodeAndPreventsReuse() {
        Long firstChatId = 456L;
        Long firstTelegramId = 123L;
        Long secondChatId = 789L;
        Long secondTelegramId = 321L;
        Long appUserId = 42L;
        String code = codeManager.generateCode(appUserId);

        handler.handle(update(firstChatId, firstTelegramId, "/link " + code));
        handler.handle(update(secondChatId, secondTelegramId, "/link " + code));

        ArgumentCaptor<TelegramUserEntity> entityCaptor = ArgumentCaptor.forClass(TelegramUserEntity.class);
        verify(telegramUserRepository, times(1)).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getTelegramId()).isEqualTo(firstTelegramId);
        assertThat(entityCaptor.getValue().getUserId()).isEqualTo(appUserId);
        assertThat(entityCaptor.getValue().getChatId()).isEqualTo(firstChatId);

        verify(codeManager).invalidateCode(code);
        assertThat(codeManager.getUserIdByCode(code)).isEmpty();
        verify(botService).sendMessage(firstChatId, TelegramMessages.LINK_SUCCESS);
        verify(botService).sendMessage(secondChatId, TelegramMessages.LINK_INVALID);
    }

    private Update update(Long chatId, Long telegramId, String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User from = mock(User.class);

        when(update.getMessage()).thenReturn(message);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(telegramId);

        return update;
    }
}
