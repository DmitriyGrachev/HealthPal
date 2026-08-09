package com.fit.fitnessapp.telegram.application.service.handlers;

final class TelegramCommandParser {

    private TelegramCommandParser() {
    }

    static boolean isCommand(String text, String command) {
        if (text == null || command == null) {
            return false;
        }
        return text.equals(command) || text.startsWith(command + " ");
    }
}
