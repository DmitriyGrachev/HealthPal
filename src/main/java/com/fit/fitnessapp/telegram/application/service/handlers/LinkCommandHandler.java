package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramLinkCodeManager;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.OffsetDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LinkCommandHandler implements CommandHandler {

    private final TelegramBotService botService;
    private final TelegramLinkCodeManager codeManager;
    private final TelegramUserRepository telegramUserRepository;

    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage() && update.getMessage().hasText() 
                && update.getMessage().getText().startsWith("/link");
    }

    @Override
    public void handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();

        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            botService.sendMessage(chatId, "Please provide the linking code: `/link 123456`.");
            return;
        }

        String code = parts[1];
        Optional<Long> userIdOpt = codeManager.getUserIdByCode(code);

        if (userIdOpt.isPresent()) {
            Long userId = userIdOpt.get();
            
            TelegramUserEntity entity = TelegramUserEntity.builder()
                    .telegramId(telegramId)
                    .userId(userId)
                    .chatId(chatId)
                    .linkedAt(OffsetDateTime.now())
                    .build();
            
            telegramUserRepository.save(entity);
            codeManager.invalidateCode(code);
            
            botService.sendMessage(chatId, "Success! 🎉 Your account is now linked. You can start using FitnessApp commands.");
        } else {
            botService.sendMessage(chatId, "Invalid or expired code. Please generate a new one on the website.");
        }
    }

    @Override
    public String getCommand() {
        return "/link";
    }
}
