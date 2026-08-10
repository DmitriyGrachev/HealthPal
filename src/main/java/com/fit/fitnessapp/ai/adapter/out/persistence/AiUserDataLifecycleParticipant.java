package com.fit.fitnessapp.ai.adapter.out.persistence;

import com.fit.fitnessapp.api.UserDataExportFragment;
import com.fit.fitnessapp.api.UserDataLifecycleParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiUserDataLifecycleParticipant implements UserDataLifecycleParticipant {

    private final JdbcTemplate jdbc;

    @Override
    public String key() {
        return "ai";
    }

    @Override
    public UserDataExportFragment exportData(Long userId) {
        return new UserDataExportFragment(key(), Map.of(
                "aiInsights", jdbc.queryForList(
                        "SELECT * FROM ai_insights WHERE user_id = ? ORDER BY date DESC", userId),
                "aiUsageBudget", jdbc.queryForList("""
                        SELECT scope_type, scope_id, window_start, used_tokens
                          FROM ai_usage_budget
                         WHERE scope_type = 'USER' AND scope_id = ?
                         ORDER BY window_start
                        """, userId)));
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.update("DELETE FROM ai_insights WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM ai_usage_budget WHERE scope_type = 'USER' AND scope_id = ?", userId);
    }
}
