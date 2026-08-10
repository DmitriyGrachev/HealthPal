package com.fit.fitnessapp.memory.adapter.out.persistence;

import com.fit.fitnessapp.api.UserDataExportFragment;
import com.fit.fitnessapp.api.UserDataLifecycleParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MemoryUserDataLifecycleParticipant implements UserDataLifecycleParticipant {

    private final JdbcTemplate jdbc;

    @Override
    public String key() {
        return "memory";
    }

    @Override
    public UserDataExportFragment exportData(Long userId) {
        return new UserDataExportFragment(key(), Map.of(
                "memories", jdbc.queryForList("""
                        SELECT id, content, metadata
                          FROM user_memory
                         WHERE user_id = ?
                        """, userId)));
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.update("DELETE FROM user_memory WHERE user_id = ?", userId);
    }
}
