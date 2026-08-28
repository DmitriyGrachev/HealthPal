package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.api.TelegramNoteRequestedEvent;
import com.fit.fitnessapp.infrastructure.events.TransactionalEventPublisher;
import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class NoteCommandHandler implements CommandHandler {

    private static final Set<String> NOTE_TYPES = Set.of(
            "ILLNESS", "TRAVEL", "INJURY", "STRESS", "ALLERGY", "GOAL",
            "PREFERENCE", "TRAINING", "NUTRITION", "GENERAL", "MOOD", "OTHER");

    private final TelegramBotService bot;
    private final ConversationStateUseCase states;
    private final TransactionalEventPublisher events;

    @org.springframework.beans.factory.annotation.Autowired
    public NoteCommandHandler(
            TelegramBotService bot,
            ConversationStateUseCase states,
            TransactionalEventPublisher events) {
        this.bot = bot;
        this.states = states;
        this.events = events;
    }

    public NoteCommandHandler(
            TelegramBotService bot,
            ConversationStateUseCase states,
            org.springframework.context.ApplicationEventPublisher events) {
        this(bot, states, new TransactionalEventPublisher(events));
    }

    @Override
    public boolean canHandle(InboundCommand command) {
        if (command.type() == InboundCommand.Type.NOTE) {
            return true;
        }
        if (command.type() != InboundCommand.Type.TEXT) {
            return false;
        }
        ConversationState state = states.getState(command.chatId());
        return state == ConversationState.WAITING_NOTE_TYPE
                || state == ConversationState.WAITING_NOTE_CONTENT;
    }

    @Override
    public void handle(InboundCommand command) {
        ConversationState state = states.getState(command.chatId());
        if (command.type() == InboundCommand.Type.NOTE) {
            start(command);
        } else if (state == ConversationState.WAITING_NOTE_TYPE) {
            acceptType(command);
        } else if (state == ConversationState.WAITING_NOTE_CONTENT) {
            acceptContent(command);
        }
    }

    private void start(InboundCommand command) {
        if (states.updateState(command.userId(), command.chatId(), ConversationState.WAITING_NOTE_TYPE)) {
            bot.enqueueOwnedMessage(command.userId(), command.chatId(), TelegramMessages.NOTE_TYPE_PROMPT);
        }
    }

    private void acceptType(InboundCommand command) {
        String type = command.payload().toUpperCase(Locale.ROOT);
        if (!NOTE_TYPES.contains(type)) {
            bot.enqueueOwnedMessage(
                    command.userId(), command.chatId(), TelegramMessages.NOTE_TYPE_INVALID);
            return;
        }
        if (states.updateState(command.userId(), command.chatId(),
                ConversationState.WAITING_NOTE_CONTENT, Map.of("noteType", type))) {
            bot.enqueueOwnedMessage(
                    command.userId(), command.chatId(), TelegramMessages.NOTE_CONTENT_PROMPT);
        }
    }

    private void acceptContent(InboundCommand command) {
        Object storedType = states.getData(command.chatId()).get("noteType");
        if (!(storedType instanceof String type) || !NOTE_TYPES.contains(type)) {
            states.clearState(command.chatId());
            bot.enqueueOwnedMessage(
                    command.userId(), command.chatId(), TelegramMessages.NOTE_TYPE_INVALID);
            return;
        }
        events.publish(new TelegramNoteRequestedEvent(
                command.userId(), command.chatId(), command.payload(), type));
        bot.enqueueOwnedMessage(command.userId(), command.chatId(),
                TelegramMessages.noteSaved(type, command.payload()));
        states.clearState(command.chatId());
    }

}
