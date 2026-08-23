package com.fit.fitnessapp.infrastructure.events;

import com.fit.fitnessapp.api.lifecycle.DataRetentionDisclosure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventPublicationUserDataLifecycleParticipantTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void exportsOnlyOwnerFilteredSafePublicationReceipts() {
        UUID id = UUID.fromString("68e9ca4f-49f5-4891-98d8-16de24b4ddde");
        Map<String, Object> receipt = Map.of(
                "id", id,
                "listenerId", "daily-insight-listener",
                "eventType", "com.example.NutritionSyncedEvent",
                "publicationDate", OffsetDateTime.parse("2026-08-23T12:00:00Z"));
        when(jdbc.queryForList(anyString(), eq(42L))).thenReturn(List.of(receipt));
        EventPublicationUserDataLifecycleParticipant participant =
                new EventPublicationUserDataLifecycleParticipant(jdbc);

        var export = participant.exportData(42L);

        assertThat(export.values()).containsEntry("eventPublicationReceipts", List.of(receipt));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(42L));
        assertThat(sql.getValue())
                .contains("publication_date")
                .contains("completion_date")
                .contains("WHERE user_id = ?")
                .doesNotContain("serialized_event");
        assertThat(participant.retentionDisclosure())
                .singleElement()
                .extracting(DataRetentionDisclosure::retentionClass)
                .isEqualTo(DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME);
    }

    @Test
    void deletesReceiptsByEnforcedOwnerColumnWithoutParsingPayload() {
        EventPublicationUserDataLifecycleParticipant participant =
                new EventPublicationUserDataLifecycleParticipant(jdbc);

        participant.deleteData(42L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq(42L));
        assertThat(sql.getValue())
                .contains("WHERE user_id = ?")
                .doesNotContain("serialized_event");
    }
}
