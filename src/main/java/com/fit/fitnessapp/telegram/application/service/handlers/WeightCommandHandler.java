package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.api.TelegramWeightRequestedEvent;
import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import com.fit.fitnessapp.infrastructure.events.TransactionalEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Component
public class WeightCommandHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(WeightCommandHandler.class);

    private final TelegramBotService botService;
    private final TelegramUserRepository telegramUserRepository;
    private final ConversationStateUseCase stateUseCase;
    private final TransactionalEventPublisher eventPublisher;
    private final UserTimeApi userTimeApi;

    @org.springframework.beans.factory.annotation.Autowired
    public WeightCommandHandler(TelegramBotService botService,
                                 TelegramUserRepository telegramUserRepository,
                                 ConversationStateUseCase stateUseCase,
                                 TransactionalEventPublisher eventPublisher,
                                 UserTimeApi userTimeApi) {
        this.botService = botService;
        this.telegramUserRepository = telegramUserRepository;
        this.stateUseCase = stateUseCase;
        this.eventPublisher = eventPublisher;
        this.userTimeApi = userTimeApi;
    }

    public WeightCommandHandler(TelegramBotService botService,
                                TelegramUserRepository telegramUserRepository,
                                ConversationStateUseCase stateUseCase,
                                org.springframework.context.ApplicationEventPublisher eventPublisher,
                                UserTimeApi userTimeApi) {
        this(botService, telegramUserRepository, stateUseCase,
                new TransactionalEventPublisher(eventPublisher), userTimeApi);
    }

    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return false;
        if (!Boolean.TRUE.equals(update.getMessage().getChat().isUserChat())) return false;

        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();
        Optional<TelegramUserEntity> userOpt = telegramUserRepository.findById(telegramId);
        if (userOpt.isEmpty() || !chatId.equals(userOpt.get().getChatId())) {
            return false;
        }
        ConversationState currentState = stateUseCase.getState(chatId);

        return TelegramCommandParser.isCommand(text, "/weight") || currentState == ConversationState.WAITING_WEIGHT;
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
        ConversationState state = stateUseCase.getState(chatId);
        Long userId = userOpt.get().getUserId();

        if (TelegramCommandParser.isCommand(text, "/weight")) {
            startWeightFlow(userId, chatId);
        } else if (state == ConversationState.WAITING_WEIGHT) {
            handleWeightInput(chatId, userId, text);
        }
    }

    private void startWeightFlow(Long userId, Long chatId) {
        if (!stateUseCase.updateState(userId, chatId, ConversationState.WAITING_WEIGHT)) {
            return;
        }
        botService.sendMessage(chatId, TelegramMessages.WEIGHT_PROMPT);
    }

    private void handleWeightInput(Long chatId, Long userId, String input) {
        try {
            String normalizedInput = input.replace(',', '.');
            BigDecimal weight = new BigDecimal(normalizedInput);

            if (weight.compareTo(BigDecimal.ZERO) <= 0 || weight.compareTo(new BigDecimal("500")) > 0) {
                botService.sendMessage(chatId, TelegramMessages.WEIGHT_UNREALISTIC);
                return;
            }

            eventPublisher.publish(new TelegramWeightRequestedEvent(
                    userId,
                    chatId,
                    weight,
                    userTimeApi.currentDate(userId)
            ));

            String formattedWeight = weight.stripTrailingZeros().toPlainString();
            botService.enqueueOwnedMessage(userId, chatId, TelegramMessages.weightRecorded(formattedWeight));
            stateUseCase.clearState(chatId);

        } catch (NumberFormatException e) {
            botService.sendMessage(chatId, TelegramMessages.WEIGHT_INVALID_FORMAT);
        }
    }

    @Override
    public String getCommand() {
        return "/weight";
    }
}
