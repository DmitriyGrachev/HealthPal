package com.fit.fitnessapp.memory;

import com.fit.fitnessapp.api.InsightDeletedEvent;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemoryEventListenerTest {

    @Mock
    private VectorStore vectorStore;

    @Test
    void insightGeneratedUpsertsStableMemoryDocument() {
        MemoryEventListener listener = new MemoryEventListener(vectorStore);
        LocalDate date = LocalDate.of(2026, 7, 6);

        listener.onInsightGenerated(new InsightGeneratedEvent(
                42L,
                date,
                InsightType.DAILY,
                "Daily summary",
                "Telegram summary",
                "snapshot-hash-1"));

        String expectedDocumentId = "insight:42:DAILY:2026-07-06";
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        InOrder inOrder = inOrder(vectorStore);
        inOrder.verify(vectorStore).delete(List.of(expectedDocumentId));
        inOrder.verify(vectorStore).add(documentsCaptor.capture());

        Document document = documentsCaptor.getValue().getFirst();
        assertThat(document.getId()).isEqualTo(expectedDocumentId);
        assertThat(document.getText()).isEqualTo("Daily summary");
        assertThat(document.getMetadata())
                .containsEntry("insight_memory_id", expectedDocumentId)
                .containsEntry("snapshot_hash", "snapshot-hash-1")
                .containsEntry("user_id", 42L)
                .containsEntry("insight_type", "DAILY");
    }

    @Test
    void insightDeletedRemovesStableMemoryDocument() {
        MemoryEventListener listener = new MemoryEventListener(vectorStore);
        LocalDate date = LocalDate.of(2026, 7, 6);

        listener.onInsightDeleted(new InsightDeletedEvent(42L, date, InsightType.DAILY));

        verify(vectorStore).delete(List.of("insight:42:DAILY:2026-07-06"));
    }
}
