package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import com.fit.fitnessapp.infrastructure.events.TransactionalEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class NoteCommandHandler implements CommandHandler {

    private final TelegramBotService botService;
    private final TelegramUserRepository telegramUserRepository;
    private final ConversationStateUseCase stateUseCase;
    private final TransactionalEventPublisher eventPublisher;
    private static final Set<String> NOTE_TYPES = Set.of(
            "ILLNESS",
            "TRAVEL",
            "INJURY",
            "STRESS",
            "ALLERGY",
            "GOAL",
            "PREFERENCE",
            "TRAINING",
            "NUTRITION",
            "GENERAL",
            "MOOD",
            "OTHER"
    );

    @org.springframework.beans.factory.annotation.Autowired
    public NoteCommandHandler(TelegramBotService botService,
                              TelegramUserRepository telegramUserRepository,
                              ConversationStateUseCase stateUseCase,
                              TransactionalEventPublisher eventPublisher) {
        this.botService = botService;
        this.telegramUserRepository = telegramUserRepository;
        this.stateUseCase = stateUseCase;
        this.eventPublisher = eventPublisher;
    }

    public NoteCommandHandler(TelegramBotService botService,
                              TelegramUserRepository telegramUserRepository,
                              ConversationStateUseCase stateUseCase,
                              org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this(botService, telegramUserRepository, stateUseCase, new TransactionalEventPublisher(eventPublisher));
    }

    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }
        if (!Boolean.TRUE.equals(update.getMessage().getChat().isUserChat())) {
            return false;
        }

        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();
        Optional<TelegramUserEntity> userOpt = telegramUserRepository.findById(telegramId);
        if (userOpt.isEmpty() || !chatId.equals(userOpt.get().getChatId())) {
            return false;
        }
        ConversationState currentState = stateUseCase.getState(chatId);

        return TelegramCommandParser.isCommand(text, "/note")
                || currentState == ConversationState.WAITING_NOTE_TYPE
                || currentState == ConversationState.WAITING_NOTE_CONTENT;
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

        if (TelegramCommandParser.isCommand(text, "/note")) {
            startNoteFlow(userId, chatId);
        } else if (state == ConversationState.WAITING_NOTE_TYPE) {
            handleNoteType(userId, chatId, text);
        } else if (state == ConversationState.WAITING_NOTE_CONTENT) {
            handleNoteContent(chatId, userId, text);
        }
    }

    private void startNoteFlow(Long userId, Long chatId) {
        if (!stateUseCase.updateState(userId, chatId, ConversationState.WAITING_NOTE_TYPE)) {
            return;
        }

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

    private void handleNoteType(Long userId, Long chatId, String type) {
        String normalizedType = type.trim().toUpperCase();
        if (!NOTE_TYPES.contains(normalizedType)) {
            botService.sendMessage(chatId, TelegramMessages.NOTE_TYPE_INVALID);
            return;
        }
        boolean updated = stateUseCase.updateState(
                userId,
                chatId,
                ConversationState.WAITING_NOTE_CONTENT,
                java.util.Map.of("noteType", normalizedType));
        if (!updated) {
            return;
        }
        botService.sendMessage(chatId, TelegramMessages.NOTE_CONTENT_PROMPT);
    }

    private void handleNoteContent(Long chatId, Long userId, String content) {
        var data = stateUseCase.getData(chatId);
        String type = (String) data.get("noteType");

        eventPublisher.publish(new com.fit.fitnessapp.api.TelegramNoteRequestedEvent(
                userId,
                chatId,
                content,
                type.toUpperCase()
        ));

        botService.enqueueOwnedMessage(userId, chatId, TelegramMessages.noteSaved(type, content));
        stateUseCase.clearState(chatId);
    }

    @Override
    public String getCommand() {
        return "/note";
    }
}
