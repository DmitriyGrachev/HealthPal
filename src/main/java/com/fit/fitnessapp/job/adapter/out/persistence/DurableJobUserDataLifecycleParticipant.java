package com.fit.fitnessapp.job.adapter.out.persistence;

import com.fit.fitnessapp.api.UserDataExportFragment;
import com.fit.fitnessapp.api.UserDataLifecycleParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DurableJobUserDataLifecycleParticipant implements UserDataLifecycleParticipant {

    private final JdbcTemplate jdbc;

    @Override
    public String key() {
        return "jobs";
    }

    @Override
    public UserDataExportFragment exportData(Long userId) {
        return new UserDataExportFragment(key(), Map.of(
                "durableJobs", jdbc.queryForList("""
                        SELECT * FROM durable_jobs WHERE user_id = ? ORDER BY created_at
                        """, userId)));
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.update("DELETE FROM durable_jobs WHERE user_id = ?", userId);
    }
}
