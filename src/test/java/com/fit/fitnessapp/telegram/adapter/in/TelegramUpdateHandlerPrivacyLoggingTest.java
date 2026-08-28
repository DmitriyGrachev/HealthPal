package com.fit.fitnessapp.telegram.adapter.in;

import com.fit.fitnessapp.telegram.application.port.in.CommandKernel;
import com.fit.fitnessapp.telegram.application.port.in.CommandResult;
import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.infrastructure.config.TelegramProperties;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class TelegramUpdateHandlerPrivacyLoggingTest {

    @Mock
    private CommandKernel kernel;
    @Mock
    private TelegramUserRepository users;
    @Mock
    private TelegramBotService bot;

    private TelegramUpdateHandler updateHandler;

    @BeforeEach
    void setUp() {
        TelegramProperties properties = new TelegramProperties();
        properties.setUsername("test_bot");
        properties.setToken("123456:test-token");
        updateHandler = new TelegramUpdateHandler(properties, kernel, users, bot);
    }

    @Test
    void normalizesLinkedPrivateUpdateOnceWithoutLoggingRawText(CapturedOutput output) {
        String sensitiveText = "/ask I binged 3200 calories and my glucose spiked";
        Update update = textUpdate(77, 100L, 900L, sensitiveText, true);
        when(users.findById(900L)).thenReturn(Optional.of(TelegramUserEntity.builder()
                .telegramId(900L).userId(11L).chatId(100L).build()));
        when(kernel.dispatch(any())).thenReturn(
                CommandResult.handled(InboundCommand.Type.ASK, "AskCommandHandler"));

        updateHandler.consume(update);

        ArgumentCaptor<InboundCommand> command = ArgumentCaptor.forClass(InboundCommand.class);
        verify(kernel).dispatch(command.capture());
        assertThat(command.getValue()).isEqualTo(new InboundCommand(
                77, 100L, 900L, 11L, InboundCommand.Type.ASK,
                "I binged 3200 calories and my glucose spiked"));
        assertThat(output)
                .contains("chatId=100")
                .contains("command=ASK")
                .doesNotContain(sensitiveText)
                .doesNotContain("3200 calories")
                .doesNotContain("glucose");
    }

    @Test
    void rejectsMissingCurrentLinkBeforeProductDispatch(CapturedOutput output) {
        Update update = textUpdate(78, 200L, 901L, "/goal private health objective", true);
        when(users.findById(901L)).thenReturn(Optional.of(TelegramUserEntity.builder()
                .telegramId(901L).userId(12L).chatId(999L).build()));

        updateHandler.consume(update);

        verifyNoInteractions(kernel);
        verify(bot).sendMessage(200L, TelegramMessages.LINK_REQUIRED);
        assertThat(output)
                .contains("command=GOAL")
                .contains("status=link_required")
                .doesNotContain("private health objective");
    }

    @Test
    void ignoresNonPrivateTextBeforeLinkLookupOrDispatch(CapturedOutput output) {
        Update update = textUpdate(79, 300L, 902L, "private note for group", false);

        updateHandler.consume(update);

        verifyNoInteractions(users, kernel, bot);
        assertThat(output).doesNotContain("private note for group");
    }

    private static Update textUpdate(int updateId, Long chatId, Long senderId, String text, boolean privateChat) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User sender = mock(User.class);

        lenient().when(update.getUpdateId()).thenReturn(updateId);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        lenient().when(message.hasText()).thenReturn(true);
        lenient().when(message.getText()).thenReturn(text);
        lenient().when(message.getChatId()).thenReturn(chatId);
        when(message.getChat()).thenReturn(chat);
        lenient().when(message.getFrom()).thenReturn(sender);
        lenient().when(sender.getId()).thenReturn(senderId);
        when(chat.isUserChat()).thenReturn(privateChat);
        return update;
    }
}
