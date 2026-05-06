package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
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
                && update.getMessage().getText().startsWith("/start");
    }

    @Override
    public void handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();

        boolean isLinked = telegramUserRepository.existsById(telegramId);

        if (isLinked) {
            botService.sendMessage(chatId, 
                "Welcome back to *FitnessApp*! 🚀\n\n" +
                "Your account is linked. You can use commands like:\n" +
                "/today - Get daily insights\n" +
                "/week - Get weekly report\n" +
                "/note - Save a quick note\n" +
                "/weight - Log your weight");
        } else {
            botService.sendMessage(chatId, 
                "Hello! Welcome to *FitnessApp Bot*! 🏋️‍♂️\n\n" +
                "To start tracking your progress here, you need to link your account.\n\n" +
                "1. Go to the FitnessApp Web Dashboard.\n" +
                "2. Find the 'Link Telegram' section.\n" +
                "3. Use the command `/link <your_code>` here.");
        }
    }

    @Override
    public String getCommand() {
        return "/start";
    }
}
