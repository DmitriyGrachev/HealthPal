package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.api.TelegramTodayRequestedEvent;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TodayCommandHandler implements CommandHandler {

    private final TelegramBotService botService;
    private final TelegramUserRepository telegramUserRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage() && update.getMessage().hasText()
                && update.getMessage().getText().startsWith("/today");
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        log.info("TodayCommandHandler: Handling request for telegramId: {}", telegramId);

        Optional<TelegramUserEntity> userOpt = telegramUserRepository.findById(telegramId);

        if (userOpt.isPresent()) {
            Long userId = userOpt.get().getUserId();
            log.info("TodayCommandHandler: User found. userId: {}. Publishing event.", userId);
            botService.sendMessage(chatId, TelegramMessages.TODAY_GENERATING);

            eventPublisher.publishEvent(new TelegramTodayRequestedEvent(
                    userId,
                    chatId,
                    LocalDate.now()
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
