package com.fit.fitnessapp.telegram.adapter.in;

import com.fit.fitnessapp.telegram.application.port.in.CommandKernel;
import com.fit.fitnessapp.telegram.application.port.in.CommandResult;
import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.infrastructure.config.TelegramProperties;
import com.fit.fitnessapp.telegram.infrastructure.persistence.entity.TelegramUserEntity;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.Optional;

@Slf4j
@Component
public class TelegramUpdateHandler implements LongPollingSingleThreadUpdateConsumer {

    private final CommandKernel kernel;
    private final TelegramUserRepository users;
    private final TelegramBotService bot;

    public TelegramUpdateHandler(
            TelegramProperties properties,
            CommandKernel kernel,
            TelegramUserRepository users,
            TelegramBotService bot) {
        this.kernel = kernel;
        this.users = users;
        this.bot = bot;
        log.info("Telegram Bot Handler initialized username={}", properties.getUsername());
    }

    @Override
    public void consume(Update update) {
        if (update == null || !update.hasMessage()) {
            log.debug("Telegram update ignored status=no_message");
            return;
        }

        Message message = update.getMessage();
        if (message.getChat() == null || !Boolean.TRUE.equals(message.getChat().isUserChat())) {
            log.debug("Telegram update ignored status=non_private_chat");
            return;
        }
        Long chatId = message.getChatId();
        if (!message.hasText()) {
            log.debug("Telegram update ignored chatId={} status=no_text", chatId);
            return;
        }
        if (message.getFrom() == null || message.getFrom().getId() == null) {
            log.debug("Telegram update ignored chatId={} status=no_sender", chatId);
            return;
        }

        Long senderId = message.getFrom().getId();
        Optional<TelegramUserEntity> currentLink = users.findById(senderId)
                .filter(link -> chatId.equals(link.getChatId()));
        Long userId = currentLink.map(TelegramUserEntity::getUserId).orElse(null);
        InboundCommand command = TelegramCommandNormalizer.normalize(
                update.getUpdateId(), chatId, senderId, userId, message.getText());

        if (command.type().currentLinkRequired() && userId == null) {
            bot.sendMessage(chatId, TelegramMessages.LINK_REQUIRED);
            log.info("Telegram command rejected chatId={} command={} status=link_required",
                    chatId, command.type());
            return;
        }

        CommandResult result = kernel.dispatch(command);
        log.info("Telegram command dispatched chatId={} command={} handler={} status={} errorCode={}",
                chatId, result.commandType(), result.handler(), result.status(), result.errorCode());
    }
}
