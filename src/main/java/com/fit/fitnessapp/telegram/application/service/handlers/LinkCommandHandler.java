package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramLinkCodeManager;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.OffsetDateTime;
import java.time.Clock;
import java.util.Optional;

@Component
public class LinkCommandHandler implements CommandHandler {

    private final TelegramBotService botService;
    private final TelegramLinkCodeManager codeManager;
    private final TelegramUserRepository telegramUserRepository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public LinkCommandHandler(TelegramBotService botService, TelegramLinkCodeManager codeManager,
                              TelegramUserRepository telegramUserRepository, Clock clock) {
        this.botService = botService;
        this.codeManager = codeManager;
        this.telegramUserRepository = telegramUserRepository;
        this.clock = clock;
    }

    public LinkCommandHandler(TelegramBotService botService, TelegramLinkCodeManager codeManager,
                              TelegramUserRepository telegramUserRepository) {
        this(botService, codeManager, telegramUserRepository, Clock.systemUTC());
    }

    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage() && update.getMessage().hasText()
                && TelegramCommandParser.isCommand(update.getMessage().getText(), "/link");
    }

    @Override
    public void handle(Update update) {
        if (update.getMessage().getChat() == null
                || !Boolean.TRUE.equals(update.getMessage().getChat().isUserChat())) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();

        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            botService.sendMessage(chatId, TelegramMessages.LINK_CODE_REQUIRED);
            return;
        }

        String code = parts[1];
        if (codeManager.isLinkAttemptLocked(chatId)) {
            botService.sendMessage(chatId, TelegramMessages.LINK_RATE_LIMITED);
            return;
        }

        Optional<Long> userIdOpt = codeManager.getUserIdByCode(code);

        if (userIdOpt.isPresent()) {
            Long userId = userIdOpt.get();

            TelegramUserEntity entity = TelegramUserEntity.builder()
                    .telegramId(telegramId)
                    .userId(userId)
                    .chatId(chatId)
                    .linkedAt(OffsetDateTime.now(clock))
                    .build();

            telegramUserRepository.save(entity);
            codeManager.invalidateCode(code);
            codeManager.clearInvalidLinkAttempts(chatId);

            botService.sendMessage(chatId, TelegramMessages.LINK_SUCCESS);
        } else {
            codeManager.recordInvalidLinkAttempt(chatId);
            String response = codeManager.isLinkAttemptLocked(chatId)
                    ? TelegramMessages.LINK_RATE_LIMITED
                    : TelegramMessages.LINK_INVALID;
            botService.sendMessage(chatId, response);
        }
    }

    @Override
    public String getCommand() {
        return "/link";
    }
}
