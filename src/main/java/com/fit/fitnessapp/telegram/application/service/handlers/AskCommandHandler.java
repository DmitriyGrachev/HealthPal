package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.api.AiRateLimitApi;
import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AskCommandHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(AskCommandHandler.class);

    private final TelegramBotService botService;
    private final TelegramUserRepository telegramUserRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AiRateLimitApi aiRateLimitApi;

    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage() && update.getMessage().hasText()
                && Boolean.TRUE.equals(update.getMessage().getChat().isUserChat())
                && update.getMessage().getText().startsWith("/ask");
    }

    @Override
    public void handle(Update update) {
        if (update.getMessage().getChat() == null || !Boolean.TRUE.equals(update.getMessage().getChat().isUserChat())) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();

        Optional<TelegramUserEntity> userOpt = telegramUserRepository.findById(telegramId);
        if (userOpt.isEmpty()) {
            botService.sendMessage(chatId, TelegramMessages.LINK_REQUIRED);
            return;
        }
        if (!chatId.equals(userOpt.get().getChatId())) {
            return;
        }

        String question = text.replace("/ask", "").trim();
        if (question.isEmpty()) {
            botService.sendMessage(chatId, TelegramMessages.ASK_REQUIRED);
            return;
        }

        Long userId = userOpt.get().getUserId();
        if (!aiRateLimitApi.tryConsume(userId)) {
            log.info("Telegram AI request rate limited userId={} chatId={}", userId, chatId);
            botService.sendMessage(chatId, TelegramMessages.ASK_RATE_LIMITED);
            return;
        }

        botService.sendMessage(chatId, TelegramMessages.ASK_THINKING);

        eventPublisher.publishEvent(new TelegramAskRequestedEvent(
                userId,
                chatId,
                question
        ));
    }

    @Override
    public String getCommand() {
        return "/ask";
    }
}
