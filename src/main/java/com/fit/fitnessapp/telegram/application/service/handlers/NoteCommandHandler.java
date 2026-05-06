package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;
import java.util.Optional;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NoteCommandHandler implements CommandHandler {

    private final TelegramBotService botService;
    private final TelegramUserRepository telegramUserRepository;
    private final ConversationStateUseCase stateUseCase;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return false;
        
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        ConversationState currentState = stateUseCase.getState(chatId);

        return text.startsWith("/note") || 
               currentState == ConversationState.WAITING_NOTE_TYPE || 
               currentState == ConversationState.WAITING_NOTE_CONTENT;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();
        ConversationState state = stateUseCase.getState(chatId);

        // Security check: must be linked
        Optional<com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity> userOpt = telegramUserRepository.findById(telegramId);
        if (userOpt.isEmpty()) {
            botService.sendMessage(chatId, "Please link your account first using `/link`.");
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
                                new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton("Training"),
                                new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton("Nutrition")
                        )),
                        new KeyboardRow(List.of(
                                new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton("General"),
                                new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton("Mood")
                        ))
                ))
                .oneTimeKeyboard(true)
                .resizeKeyboard(true)
                .build();

        botService.sendMessage(chatId, "What type of note is this?", keyboard);
    }

    private void handleNoteType(Long chatId, String type) {
        stateUseCase.updateState(chatId, ConversationState.WAITING_NOTE_CONTENT, java.util.Map.of("noteType", type));
        botService.sendMessage(chatId, "Got it. Now, what would you like to record?");
    }

    private void handleNoteContent(Long chatId, Long userId, String content) {
        var data = stateUseCase.getData(chatId);
        String type = (String) data.get("noteType");
        
        eventPublisher.publishEvent(new com.fit.fitnessapp.telegram.api.TelegramNoteRequestedEvent(
                userId,
                chatId,
                content,
                type.toUpperCase()
        ));
        
        botService.sendMessage(chatId, "Note saved! 📝\nType: " + type + "\nContent: " + content);
        
        stateUseCase.clearState(chatId);
    }

    @Override
    public String getCommand() {
        return "/note";
    }
}
