package com.fit.fitnessapp.auth.adapter.out.persistence;

import com.fit.fitnessapp.api.UserDataExportFragment;
import com.fit.fitnessapp.api.UserDataLifecycleParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthUserDataLifecycleParticipant implements UserDataLifecycleParticipant {

    private final JdbcTemplate jdbc;

    @Override
    public String key() {
        return "auth";
    }

    @Override
    public UserDataExportFragment exportData(Long userId) {
        return new UserDataExportFragment(key(), Map.of(
                "userNotes", jdbc.queryForList("""
                        SELECT id, type, content, related_date, created_at
                          FROM user_notes
                         WHERE user_id = ?
                         ORDER BY related_date DESC
                        """, userId)));
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.update("DELETE FROM user_notes WHERE user_id = ?", userId);
    }
}
