package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramLinkCodeManager;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class TestGenerateHandler implements CommandHandler {

    private final TelegramBotService botService;
    private final TelegramLinkCodeManager codeManager;

    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage() && update.getMessage().hasText() 
                && update.getMessage().getText().startsWith("/test_generate");
    }

    @Override
    public void handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        
        // Hardcoded for testing: link to User ID 1
        String code = codeManager.generateCode(1L);
        
        botService.sendMessage(chatId, TelegramMessages.testCodeGenerated(code));
    }

    @Override
    public String getCommand() {
        return "/test_generate";
    }
}
