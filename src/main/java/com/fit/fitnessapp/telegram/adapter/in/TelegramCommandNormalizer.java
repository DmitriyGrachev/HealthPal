package com.fit.fitnessapp.telegram.adapter.in;

import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;

import java.util.Locale;
import java.util.Map;

/** Converts Telegram's text protocol into the neutral command vocabulary. */
final class TelegramCommandNormalizer {

    private static final Map<String, InboundCommand.Type> COMMANDS = Map.ofEntries(
            Map.entry("/start", InboundCommand.Type.START),
            Map.entry("/link", InboundCommand.Type.LINK),
            Map.entry("/test_generate", InboundCommand.Type.TEST_GENERATE),
            Map.entry("/ask", InboundCommand.Type.ASK),
            Map.entry("/today", InboundCommand.Type.TODAY),
            Map.entry("/weight", InboundCommand.Type.WEIGHT),
            Map.entry("/note", InboundCommand.Type.NOTE),
            Map.entry("/goal", InboundCommand.Type.GOAL),
            Map.entry("/experiment", InboundCommand.Type.EXPERIMENT),
            Map.entry("/checkin", InboundCommand.Type.CHECKIN),
            Map.entry("/outcome", InboundCommand.Type.OUTCOME),
            Map.entry("/evaluate", InboundCommand.Type.EVALUATE));

    private TelegramCommandNormalizer() {
    }

    static InboundCommand normalize(
            int updateId, Long chatId, Long senderId, Long userId, String text) {
        String normalized = text == null ? "" : text.trim();
        if (!normalized.startsWith("/")) {
            return new InboundCommand(updateId, chatId, senderId, userId,
                    InboundCommand.Type.TEXT, normalized);
        }

        int separator = normalized.indexOf(' ');
        String token = separator < 0 ? normalized : normalized.substring(0, separator);
        int botSuffix = token.indexOf('@');
        if (botSuffix >= 0) {
            token = token.substring(0, botSuffix);
        }
        InboundCommand.Type type = COMMANDS.getOrDefault(
                token.toLowerCase(Locale.ROOT), InboundCommand.Type.UNKNOWN);
        String payload = separator < 0 ? "" : normalized.substring(separator + 1);
        return new InboundCommand(updateId, chatId, senderId, userId, type, payload);
    }
}
