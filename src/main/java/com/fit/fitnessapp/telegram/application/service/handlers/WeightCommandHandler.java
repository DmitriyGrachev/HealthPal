package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.api.TelegramWeightRequestedEvent;
import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.infrastructure.events.TransactionalEventPublisher;
import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class WeightCommandHandler implements CommandHandler {

    private final TelegramBotService bot;
    private final ConversationStateUseCase states;
    private final TransactionalEventPublisher events;
    private final UserTimeApi userTime;

    @org.springframework.beans.factory.annotation.Autowired
    public WeightCommandHandler(
            TelegramBotService bot,
            ConversationStateUseCase states,
            TransactionalEventPublisher events,
            UserTimeApi userTime) {
        this.bot = bot;
        this.states = states;
        this.events = events;
        this.userTime = userTime;
    }

    public WeightCommandHandler(
            TelegramBotService bot,
            ConversationStateUseCase states,
            org.springframework.context.ApplicationEventPublisher events,
            UserTimeApi userTime) {
        this(bot, states, new TransactionalEventPublisher(events), userTime);
    }

    @Override
    public boolean canHandle(InboundCommand command) {
        return command.type() == InboundCommand.Type.WEIGHT
                || command.type() == InboundCommand.Type.TEXT
                && states.getState(command.chatId()) == ConversationState.WAITING_WEIGHT;
    }

    @Override
    public void handle(InboundCommand command) {
        if (command.type() == InboundCommand.Type.WEIGHT) {
            start(command);
        } else if (states.getState(command.chatId()) == ConversationState.WAITING_WEIGHT) {
            record(command);
        }
    }

    private void start(InboundCommand command) {
        if (states.updateState(command.userId(), command.chatId(), ConversationState.WAITING_WEIGHT)) {
            bot.enqueueOwnedMessage(command.userId(), command.chatId(), TelegramMessages.WEIGHT_PROMPT);
        }
    }

    private void record(InboundCommand command) {
        try {
            BigDecimal weight = new BigDecimal(command.payload().replace(',', '.'));
            if (weight.compareTo(BigDecimal.ZERO) <= 0
                    || weight.compareTo(new BigDecimal("500")) > 0) {
                bot.enqueueOwnedMessage(
                        command.userId(), command.chatId(), TelegramMessages.WEIGHT_UNREALISTIC);
                return;
            }
            events.publish(new TelegramWeightRequestedEvent(
                    command.userId(), command.chatId(), weight,
                    userTime.currentDate(command.userId())));
            bot.enqueueOwnedMessage(command.userId(), command.chatId(),
                    TelegramMessages.weightRecorded(weight.stripTrailingZeros().toPlainString()));
            states.clearState(command.chatId());
        } catch (NumberFormatException exception) {
            bot.enqueueOwnedMessage(
                    command.userId(), command.chatId(), TelegramMessages.WEIGHT_INVALID_FORMAT);
        }
    }

}
