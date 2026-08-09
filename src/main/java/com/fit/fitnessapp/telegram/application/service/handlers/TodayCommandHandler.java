package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.api.TelegramTodayRequestedEvent;
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

import java.time.LocalDate;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TodayCommandHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(TodayCommandHandler.class);

    private final TelegramBotService botService;
    private final TelegramUserRepository telegramUserRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserTimeApi userTimeApi;

    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage() && update.getMessage().hasText()
                && Boolean.TRUE.equals(update.getMessage().getChat().isUserChat())
                && TelegramCommandParser.isCommand(update.getMessage().getText(), "/today");
    }

    @Override
    public void handle(Update update) {
        if (update.getMessage().getChat() == null || !Boolean.TRUE.equals(update.getMessage().getChat().isUserChat())) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        log.info("TodayCommandHandler: Handling request for telegramId: {}", telegramId);

        Optional<TelegramUserEntity> userOpt = telegramUserRepository.findById(telegramId);

        if (userOpt.isPresent()) {
            if (!chatId.equals(userOpt.get().getChatId())) {
                return;
            }
            Long userId = userOpt.get().getUserId();
            log.info("TodayCommandHandler: User found. userId: {}. Publishing event.", userId);
            botService.sendMessage(chatId, TelegramMessages.TODAY_GENERATING);

            eventPublisher.publishEvent(new TelegramTodayRequestedEvent(
                    userId,
                    chatId,
                    userTimeApi.currentDate(userId)
            ));
            log.info("TodayCommandHandler: Event published.");
        } else {
            log.warn("TodayCommandHandler: User not found for telegramId: {}", telegramId);
            botService.sendMessage(chatId, TelegramMessages.LINK_REQUIRED);
        }
    }

    @Override
    public String getCommand() {
        return "/today";
    }
}
