package com.fit.fitnessapp.infrastructure.events;

import com.fit.fitnessapp.api.lifecycle.DataRetentionDisclosure;
import com.fit.fitnessapp.api.lifecycle.UserDataExportFragment;
import com.fit.fitnessapp.api.lifecycle.UserDataLifecycleParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;

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
    public List<DataRetentionDisclosure> retentionDisclosure() {
        return List.of(new DataRetentionDisclosure(
                "event_publications",
                DataRetentionDisclosure.StorageClass.LOCAL_OPERATIONAL,
                DataRetentionDisclosure.RetentionClass.UNTIL_TERMINAL,
                null,
                List.of(),
                DataRetentionDisclosure.DeletionScope.LOCAL_OPERATIONAL,
                DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION));
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
