package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.api.TelegramWeightRequestedEvent;
import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeightCommandHandler implements CommandHandler {

    private final TelegramBotService botService;
    private final TelegramUserRepository telegramUserRepository;
    private final ConversationStateUseCase stateUseCase;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return false;

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        ConversationState currentState = stateUseCase.getState(chatId);

        return text.startsWith("/weight") || currentState == ConversationState.WAITING_WEIGHT;
    }

    @Override
    @Transactional
    public void handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();
        ConversationState state = stateUseCase.getState(chatId);

        Optional<TelegramUserEntity> userOpt = telegramUserRepository.findById(telegramId);
        if (userOpt.isEmpty()) {
            botService.sendMessage(chatId, TelegramMessages.LINK_REQUIRED);
            return;
        }

        if (text.startsWith("/weight")) {
            startWeightFlow(chatId);
        } else if (state == ConversationState.WAITING_WEIGHT) {
            handleWeightInput(chatId, userOpt.get().getUserId(), text);
        }
    }

    private void startWeightFlow(Long chatId) {
        stateUseCase.updateState(chatId, ConversationState.WAITING_WEIGHT);
        botService.sendMessage(chatId, TelegramMessages.WEIGHT_PROMPT);
    }

    private void handleWeightInput(Long chatId, Long userId, String input) {
        try {
            // Replace comma with dot for parsing
            String normalizedInput = input.replace(',', '.');
            BigDecimal weight = new BigDecimal(normalizedInput);

            if (weight.compareTo(BigDecimal.ZERO) <= 0 || weight.compareTo(new BigDecimal("500")) > 0) {
                botService.sendMessage(chatId, TelegramMessages.WEIGHT_UNREALISTIC);
                return;
            }

            eventPublisher.publishEvent(new TelegramWeightRequestedEvent(
                    userId,
                    chatId,
                    weight,
                    LocalDate.now()
            ));

            String formattedWeight = weight.stripTrailingZeros().toPlainString();
            botService.sendMessage(chatId, TelegramMessages.weightRecorded(formattedWeight));
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
