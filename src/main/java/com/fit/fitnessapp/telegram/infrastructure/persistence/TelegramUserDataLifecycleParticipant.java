package com.fit.fitnessapp.telegram.infrastructure.persistence;

import com.fit.fitnessapp.api.UserDataExportFragment;
import com.fit.fitnessapp.api.UserDataLifecycleParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TelegramUserDataLifecycleParticipant implements UserDataLifecycleParticipant {

    private final JdbcTemplate jdbc;

    @Override
    public String key() {
        return "telegram";
    }

    @Override
    public UserDataExportFragment exportData(Long userId) {
        List<Map<String, Object>> links = jdbc.queryForList("""
                SELECT telegram_id, chat_id, linked_at
                  FROM telegram_users
                 WHERE user_id = ?
                """, userId);
        Map<String, Object> account = links.isEmpty() ? Map.of() : links.getFirst();
        if (account.isEmpty()) {
            return emptyFragment();
        }
        Long chatId = ((Number) account.get("chat_id")).longValue();
        List<Map<String, Object>> states = jdbc.queryForList(
                "SELECT * FROM conversation_state WHERE chat_id = ?", chatId);
        return new UserDataExportFragment(key(), Map.of(
                "telegramAccount", account,
                "conversationState", states.isEmpty() ? Map.of() : states.getFirst(),
                "conversationHistory", jdbc.queryForList("""
                        SELECT * FROM conversation_history WHERE chat_id = ? ORDER BY created_at
                        """, chatId),
                "telegramDeliveries", jdbc.queryForList("""
                        SELECT * FROM telegram_delivery_outbox WHERE chat_id = ? ORDER BY created_at
                        """, chatId)));
    }

    @Override
    public void deleteData(Long userId) {
        List<Long> chatIds = jdbc.queryForList(
                "SELECT chat_id FROM telegram_users WHERE user_id = ? FOR UPDATE", Long.class, userId);
        for (Long chatId : chatIds) {
            jdbc.update("DELETE FROM conversation_history WHERE chat_id = ?", chatId);
            jdbc.update("DELETE FROM conversation_state WHERE chat_id = ?", chatId);
            jdbc.update("DELETE FROM telegram_delivery_outbox WHERE chat_id = ?", chatId);
        }
        jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", userId);
    }

    private UserDataExportFragment emptyFragment() {
        return new UserDataExportFragment(key(), Map.of(
                "telegramAccount", Map.of(),
                "conversationState", Map.of(),
                "conversationHistory", List.of(),
                "telegramDeliveries", List.of()));
    }
}
