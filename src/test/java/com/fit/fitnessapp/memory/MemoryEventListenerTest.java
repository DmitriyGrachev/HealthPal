package com.fit.fitnessapp.memory;

import com.fit.fitnessapp.api.InsightDeletedEvent;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightSourceApi;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.api.SensitiveAiEgressGuard;
import com.fit.fitnessapp.auth.api.UserDataPresenceApi;
import com.fit.fitnessapp.auth.api.UserNoteCreatedEvent;
import com.fit.fitnessapp.auth.api.UserNoteDeletedEvent;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import com.fit.fitnessapp.memory.application.service.MemoryEventListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.LocalDate;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryEventListenerTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private UserDataPresenceApi userDataPresenceApi;

    @Mock
    private InsightSourceApi insightSourceApi;

    @Mock
    private SensitiveAiEgressGuard egressGuard;

    private MemoryEventListener listener;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        listener = new MemoryEventListener(
                vectorStore,
                Clock.systemUTC(),
                userDataPresenceApi,
                insightSourceApi,
                egressGuard);
    }

    @Test
    void insightGeneratedUpsertsStableMemoryDocument() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(userDataPresenceApi.userExists(42L)).thenReturn(true);
        when(insightSourceApi.insightMatchesSnapshot(
                42L, InsightType.DAILY, date, "snapshot-hash-1")).thenReturn(true);

        listener.onInsightGenerated(new InsightGeneratedEvent(
                42L,
                date,
                InsightType.DAILY,
                "Daily summary",
                "Telegram summary",
                "snapshot-hash-1"));

        String expectedDocumentId = "insight:42:DAILY:2026-07-06";
        String expectedVectorId = UUID.nameUUIDFromBytes(expectedDocumentId.getBytes(StandardCharsets.UTF_8)).toString();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        InOrder inOrder = inOrder(vectorStore);
        inOrder.verify(vectorStore).delete(List.of(expectedVectorId));
        inOrder.verify(vectorStore).add(documentsCaptor.capture());

        Document document = documentsCaptor.getValue().getFirst();
        assertThat(document.getId()).isEqualTo(expectedVectorId);
        assertThat(document.getText()).isEqualTo("Daily summary");
        assertThat(document.getMetadata())
                .containsEntry("insight_memory_id", expectedDocumentId)
                .containsEntry("snapshot_hash", "snapshot-hash-1")
                .containsEntry("user_id", 42L)
                .containsEntry("insight_type", "DAILY");
    }

    @Test
    void deniedSensitiveEgressDoesNotAddInsightMemory() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(userDataPresenceApi.userExists(42L)).thenReturn(true);
        when(insightSourceApi.insightMatchesSnapshot(
                42L, InsightType.DAILY, date, "snapshot-hash-1")).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("denied"))
                .when(egressGuard).validateSensitiveEgress();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.onInsightGenerated(
                new InsightGeneratedEvent(
                        42L, date, InsightType.DAILY, "private", null, "snapshot-hash-1")))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(vectorStore);
    }

    @Test
    void insightDeletedRemovesStableMemoryDocument() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(userDataPresenceApi.userExists(42L)).thenReturn(true);
        when(insightSourceApi.insightExists(42L, InsightType.DAILY, date)).thenReturn(false);

        listener.onInsightDeleted(new InsightDeletedEvent(42L, date, InsightType.DAILY));

        String expectedVectorId = UUID.nameUUIDFromBytes(
                "insight:42:DAILY:2026-07-06".getBytes(StandardCharsets.UTF_8)).toString();
        verify(vectorStore).delete(List.of(expectedVectorId));
    }

    @Test
    void staleInsightVersionDoesNotTouchProviderOrCurrentProjection() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(userDataPresenceApi.userExists(42L)).thenReturn(true);
        when(insightSourceApi.insightMatchesSnapshot(
                42L, InsightType.DAILY, date, "snapshot-a")).thenReturn(false);

        listener.onInsightGenerated(new InsightGeneratedEvent(
                42L, date, InsightType.DAILY, "stale content", null, "snapshot-a"));

        verifyNoInteractions(egressGuard, vectorStore);
    }

    @Test
    void missingInsightVersionFailsClosedBeforeSourceOrProviderIo() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(userDataPresenceApi.userExists(42L)).thenReturn(true);

        for (String snapshotHash : new String[] {null, "", "   "}) {
            listener.onInsightGenerated(new InsightGeneratedEvent(
                    42L, date, InsightType.DAILY, "legacy content", null, snapshotHash));
        }

        verifyNoInteractions(insightSourceApi, egressGuard, vectorStore);
    }

    @Test
    void staleDeleteAfterSourceRecreationDoesNotRemoveCurrentProjection() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(userDataPresenceApi.userExists(42L)).thenReturn(true);
        when(insightSourceApi.insightExists(42L, InsightType.DAILY, date)).thenReturn(true);

        listener.onInsightDeleted(new InsightDeletedEvent(42L, date, InsightType.DAILY));

        verifyNoInteractions(vectorStore);
    }

    @Test
    void missingUserCompletesInsightDeleteReplayWithoutRemovingMemory() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(userDataPresenceApi.userExists(42L)).thenReturn(false);

        listener.onInsightDeleted(new InsightDeletedEvent(42L, date, InsightType.DAILY));

        verifyNoInteractions(insightSourceApi, vectorStore);
    }

    @Test
    void userNoteMemoryUsesStableSourceIdAndDeletesOnNoteDeletion() {
        UserNoteCreatedEvent created = new UserNoteCreatedEvent(
                77L,
                42L,
                LocalDate.of(2026, 7, 6),
                "peanut allergy",
                UserNoteDto.NoteType.ALLERGY);
        when(userDataPresenceApi.userExists(42L)).thenReturn(true);
        when(userDataPresenceApi.userNoteExists(42L, 77L)).thenReturn(true);

        listener.onUserNoteCreated(created);

        String expectedVectorId = UUID.nameUUIDFromBytes(
                "note:42:77".getBytes(StandardCharsets.UTF_8)).toString();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).delete(List.of(expectedVectorId));
        verify(vectorStore).add(documentsCaptor.capture());
        Document document = documentsCaptor.getValue().getFirst();
        assertThat(document.getId()).isEqualTo(expectedVectorId);
        assertThat(document.getMetadata())
                .containsEntry("source_type", "USER_NOTE")
                .containsEntry("source_id", 77L)
                .containsEntry("memory_id", "note:42:77");

        clearInvocations(vectorStore);
        listener.onUserNoteDeleted(new UserNoteDeletedEvent(42L, 77L));

        verify(vectorStore).delete(List.of(expectedVectorId));
    }

    @Test
    void missingUserCompletesInsightReplayWithoutRecreatingMemory() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(userDataPresenceApi.userExists(42L)).thenReturn(false);

        listener.onInsightGenerated(new InsightGeneratedEvent(
                42L,
                date,
                InsightType.DAILY,
                "stale content",
                "stale summary",
                "stale-snapshot"));

        verifyNoInteractions(vectorStore, insightSourceApi);
    }

    @Test
    void missingNoteSourceCompletesReplayWithoutRecreatingMemory() {
        when(userDataPresenceApi.userExists(42L)).thenReturn(true);
        when(userDataPresenceApi.userNoteExists(42L, 77L)).thenReturn(false);

        listener.onUserNoteCreated(new UserNoteCreatedEvent(
                77L,
                42L,
                LocalDate.of(2026, 7, 6),
                "stale note",
                UserNoteDto.NoteType.GENERAL));

        verifyNoInteractions(vectorStore);
    }
}
