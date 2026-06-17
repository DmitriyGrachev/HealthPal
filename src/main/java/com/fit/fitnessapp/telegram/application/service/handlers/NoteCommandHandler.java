package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NoteCommandHandler implements CommandHandler {

    private final TelegramBotService botService;
    private final TelegramUserRepository telegramUserRepository;
    private final ConversationStateUseCase stateUseCase;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        ConversationState currentState = stateUseCase.getState(chatId);

        return text.startsWith("/note")
                || currentState == ConversationState.WAITING_NOTE_TYPE
                || currentState == ConversationState.WAITING_NOTE_CONTENT;
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

        if (text.startsWith("/note")) {
            startNoteFlow(chatId);
        } else if (state == ConversationState.WAITING_NOTE_TYPE) {
            handleNoteType(chatId, text);
        } else if (state == ConversationState.WAITING_NOTE_CONTENT) {
            handleNoteContent(chatId, userOpt.get().getUserId(), text);
        }
    }

    private void startNoteFlow(Long chatId) {
        stateUseCase.updateState(chatId, ConversationState.WAITING_NOTE_TYPE);

        ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder()
                .keyboard(List.of(
                        new KeyboardRow(List.of(
                                new KeyboardButton("Training"),
                                new KeyboardButton("Nutrition")
                        )),
                        new KeyboardRow(List.of(
                                new KeyboardButton("General"),
                                new KeyboardButton("Mood")
                        ))
                ))
                .oneTimeKeyboard(true)
                .resizeKeyboard(true)
                .build();

        botService.sendMessage(chatId, TelegramMessages.NOTE_TYPE_PROMPT, keyboard);
    }

    private void handleNoteType(Long chatId, String type) {
        String normalizedType = type.trim().toUpperCase();
        stateUseCase.updateState(chatId, ConversationState.WAITING_NOTE_CONTENT, java.util.Map.of("noteType", normalizedType));
        botService.sendMessage(chatId, TelegramMessages.NOTE_CONTENT_PROMPT);
    }

    private void handleNoteContent(Long chatId, Long userId, String content) {
        var data = stateUseCase.getData(chatId);
        String type = (String) data.get("noteType");

        eventPublisher.publishEvent(new com.fit.fitnessapp.api.TelegramNoteRequestedEvent(
                userId,
                chatId,
                content,
                type.toUpperCase()
        ));

        botService.sendMessage(chatId, TelegramMessages.noteSaved(type, content));
        stateUseCase.clearState(chatId);
    }

    @Override
    public String getCommand() {
        return "/note";
    }
}
