package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class StartCommandHandler implements CommandHandler {

    private final TelegramBotService botService;
    private final TelegramUserRepository telegramUserRepository;

    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage() && update.getMessage().hasText()
                && TelegramCommandParser.isCommand(update.getMessage().getText(), "/start");
    }

    @Override
    public void handle(Update update) {
        if (update.getMessage().getChat() == null
                || !Boolean.TRUE.equals(update.getMessage().getChat().isUserChat())) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();

        boolean isLinked = telegramUserRepository.existsById(telegramId);
        botService.sendMessage(chatId, isLinked ? TelegramMessages.startLinked() : TelegramMessages.startUnlinked());
    }

    @Override
    public String getCommand() {
        return "/start";
    }
}
