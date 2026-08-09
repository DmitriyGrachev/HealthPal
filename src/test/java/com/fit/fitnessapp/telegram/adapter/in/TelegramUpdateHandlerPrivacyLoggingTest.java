package com.fit.fitnessapp.telegram.adapter.in;

import com.fit.fitnessapp.telegram.application.service.handlers.CommandHandler;
import com.fit.fitnessapp.telegram.infrastructure.config.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class TelegramUpdateHandlerPrivacyLoggingTest {

    @Mock
    private CommandHandler commandHandler;

    private TelegramUpdateHandler updateHandler;

    @BeforeEach
    void setUp() {
        TelegramProperties properties = new TelegramProperties();
        properties.setUsername("test_bot");
        properties.setToken("123456:test-token");

        updateHandler = new TelegramUpdateHandler(properties, List.of(commandHandler));
    }

    @Test
    void handledMessageDoesNotLogRawTelegramText(CapturedOutput output) {
        String sensitiveText = "/ask I binged 3200 calories and my glucose spiked";
        Update update = textUpdate(100L, sensitiveText);
        when(commandHandler.canHandle(update)).thenReturn(true);
        when(commandHandler.getCommand()).thenReturn("/ask");

        updateHandler.onUpdateReceived(update);

        assertThat(output)
                .contains("chatId=100")
                .contains("command=/ask")
                .doesNotContain(sensitiveText)
                .doesNotContain("3200 calories")
                .doesNotContain("glucose")
                .doesNotContain("Message from");

        verify(commandHandler).handle(update);
    }

    @Test
    void unknownMessageDoesNotLogRawTelegramText(CapturedOutput output) {
        String sensitiveText = "private note about weight 82.5 and medication";
        Update update = textUpdate(200L, sensitiveText);
        when(commandHandler.canHandle(update)).thenReturn(false);

        updateHandler.onUpdateReceived(update);

        assertThat(output)
                .contains("chatId=200")
                .contains("status=unhandled")
                .doesNotContain(sensitiveText)
                .doesNotContain("82.5")
                .doesNotContain("medication")
                .doesNotContain("No handler found for message");

        verify(commandHandler).canHandle(update);
        verifyNoMoreInteractions(commandHandler);
    }

    @ParameterizedTest(name = "{0} chat is ignored before handler dispatch")
    @ValueSource(strings = {"group", "supergroup", "channel"})
    void nonPrivateChatDoesNotDispatchOrLogRawTelegramText(String chatType, CapturedOutput output) {
        String sensitiveText = "private note for " + chatType + " chat";
        Update update = nonPrivateTextUpdate(300L, sensitiveText);

        updateHandler.onUpdateReceived(update);

        verifyNoInteractions(commandHandler);
        assertThat(output).doesNotContain(sensitiveText);
    }

    private Update textUpdate(Long chatId, String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        lenient().when(message.hasText()).thenReturn(true);
        lenient().when(message.getText()).thenReturn(text);
        lenient().when(message.getChatId()).thenReturn(chatId);
        lenient().when(message.getChat()).thenReturn(chat);
        lenient().when(chat.isUserChat()).thenReturn(true);

        return update;
    }

    private Update nonPrivateTextUpdate(Long chatId, String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        lenient().when(message.hasText()).thenReturn(true);
        lenient().when(message.getText()).thenReturn(text);
        lenient().when(message.getChatId()).thenReturn(chatId);
        when(message.getChat()).thenReturn(chat);
        when(chat.isUserChat()).thenReturn(false);

        return update;
    }
}
