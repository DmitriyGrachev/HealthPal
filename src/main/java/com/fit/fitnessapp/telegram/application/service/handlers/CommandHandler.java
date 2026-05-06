package com.fit.fitnessapp.telegram.application.service.handlers;

import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Strategy interface for handling Telegram commands.
 */
public interface CommandHandler {
    /**
     * Checks if this handler can process the given update.
     */
    boolean canHandle(Update update);

    /**
     * Executes the command logic.
     */
    void handle(Update update);

    /**
     * Returns the command string (e.g., "/start").
     */
    String getCommand();
}
