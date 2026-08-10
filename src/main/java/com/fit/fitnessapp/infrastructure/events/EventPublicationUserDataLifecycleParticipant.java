package com.fit.fitnessapp.infrastructure.events;

import com.fit.fitnessapp.api.UserDataExportFragment;
import com.fit.fitnessapp.api.UserDataLifecycleParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class EventPublicationUserDataLifecycleParticipant implements UserDataLifecycleParticipant {

    private final JdbcTemplate jdbc;

    @Override
    public String key() {
        return "event-publications";
    }

    @Override
    public UserDataExportFragment exportData(Long userId) {
        return new UserDataExportFragment(key(), Map.of());
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.update("""
                DELETE FROM event_publication
                 WHERE CASE
                           WHEN serialized_event IS JSON
                           THEN serialized_event::jsonb ->> 'userId'
                       END = ?
                """, userId.toString());
    }
}
