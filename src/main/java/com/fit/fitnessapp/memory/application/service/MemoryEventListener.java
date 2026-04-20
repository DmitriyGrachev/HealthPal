package com.fit.fitnessapp.memory.application.service;

import com.fit.fitnessapp.ai.api.InsightGeneratedEvent;
import com.fit.fitnessapp.auth.api.UserNoteCreatedEvent;
import com.fit.fitnessapp.memory.domain.MemoryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryEventListener {

    private final VectorStore vectorStore;

    @ApplicationModuleListener
    public void onInsightGenerated(InsightGeneratedEvent event) {
        log.info("Storing AI insight in memory for user {} and date {}", event.userId(), event.date());

        Document doc = new Document(
                event.content(),
                Map.of(
                        "user_id", event.userId(),
                        "date", event.date().toString(),
                        "insight_type", event.insightType().name(),
                        "memory_type", MemoryType.SEMANTIC.name(),
                        "created_at", Instant.now().toString()
                )
        );

        vectorStore.add(List.of(doc));
    }

    @ApplicationModuleListener
    public void onUserNoteCreated(UserNoteCreatedEvent event) {
        log.info("Storing user note in memory for user {} and date {}", event.userId(), event.date());

        Document doc = new Document(
                event.content(),
                Map.of(
                        "user_id", event.userId(),
                        "date", event.date().toString(),
                        "note_type", event.type().name(),
                        "memory_type", MemoryType.EPISODIC.name(),
                        "created_at", Instant.now().toString()
                )
        );

        vectorStore.add(List.of(doc));
    }
}
