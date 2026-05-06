package com.fit.fitnessapp.telegram.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Service
public class TelegramBotService {

    private final AbsSender botSender;

    public TelegramBotService(@Lazy AbsSender botSender) {
        this.botSender = botSender;
    }

    public void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    public void sendMessage(Long chatId, String text, ReplyKeyboard keyboard) {
        // Basic escaping for MarkdownV2 - simplified for now
        // In a real app, we'd use a more robust utility
        
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(keyboard)
                .parseMode("Markdown") // Using simpler Markdown first to avoid V2 escaping issues
                .build();
        
        try {
            botSender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to {}: {}", chatId, e.getMessage());
        }
    }
}
