package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AskCommandHandler implements CommandHandler {

    private final TelegramBotService botService;
    private final TelegramUserRepository telegramUserRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage() && update.getMessage().hasText() 
                && update.getMessage().getText().startsWith("/ask");
    }

    @Override
    @Transactional
    public void handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();

        Optional<TelegramUserEntity> userOpt = telegramUserRepository.findById(telegramId);
        if (userOpt.isEmpty()) {
            botService.sendMessage(chatId, "Please link your account first using `/link`.");
            return;
        }

        String question = text.replace("/ask", "").trim();
        if (question.isEmpty()) {
            botService.sendMessage(chatId, "Please provide a question: `/ask How much protein did I have today?`.");
            return;
        }

        botService.sendMessage(chatId, "Thinking... 🧠");

        eventPublisher.publishEvent(new TelegramAskRequestedEvent(
                userOpt.get().getUserId(),
                chatId,
                question
        ));
    }

    @Override
    public String getCommand() {
        return "/ask";
    }
}
