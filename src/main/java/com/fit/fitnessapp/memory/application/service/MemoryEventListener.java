package com.fit.fitnessapp.memory.application.service;

import com.fit.fitnessapp.api.InsightDeletedEvent;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.auth.api.UserNoteCreatedEvent;
import com.fit.fitnessapp.auth.api.UserNoteDeletedEvent;
import com.fit.fitnessapp.memory.domain.MemoryType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MemoryEventListener {

    private final VectorStore vectorStore;
    private final Clock clock;

    @Autowired
    public MemoryEventListener(VectorStore vectorStore, Clock clock) {
        this.vectorStore = vectorStore;
        this.clock = clock;
    }

    public MemoryEventListener(VectorStore vectorStore) {
        this(vectorStore, Clock.systemUTC());
    }

    @ApplicationModuleListener
    public void onInsightGenerated(InsightGeneratedEvent event) {

        // Daily insights are short-term (14 days).
        // Weekly/monthly insights are medium-term (90 days).
        Instant expiresAt = switch (event.insightType()) {
            case DAILY -> clock.instant().plus(14, ChronoUnit.DAYS);
            case WEEKLY -> clock.instant().plus(90, ChronoUnit.DAYS);
            case MONTHLY -> null; // Does not expire.
        };

        String horizon = switch (event.insightType()) {
            case DAILY -> "SHORT_TERM";
            case WEEKLY -> "MID_TERM";
            case MONTHLY -> "LONG_TERM";
        };

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("user_id", event.userId());
        metadata.put("date", event.date().toString());
        metadata.put("insight_type", event.insightType().name());
        metadata.put("memory_type", MemoryType.SEMANTIC.name());
        metadata.put("memory_horizon", horizon);
        metadata.put("created_at", clock.instant().toString());
        String sourceKey = insightMemoryId(event);
        String memoryId = stableVectorId(sourceKey);
        metadata.put("insight_memory_id", sourceKey);
        metadata.put("memory_id", sourceKey);
        metadata.put("vector_id", memoryId);
        if (event.snapshotHash() != null && !event.snapshotHash().isBlank()) {
            metadata.put("snapshot_hash", event.snapshotHash());
        }
        if (expiresAt != null) {
            metadata.put("expires_at", expiresAt.toString());
        }

        vectorStore.delete(List.of(memoryId));
        vectorStore.add(List.of(new Document(memoryId, event.content(), metadata)));
    }

    @ApplicationModuleListener
    public void onInsightDeleted(InsightDeletedEvent event) {
        vectorStore.delete(List.of(stableVectorId(
                insightMemoryId(event.userId(), event.insightType(), event.date()))));
    }

    @ApplicationModuleListener
    public void onUserNoteCreated(UserNoteCreatedEvent event) {

        // Temporary notes (illness, event) last 7 days.
        // Permanent notes (allergy, goal) never expire.
        boolean isPermanent = switch (event.type()) {
            case ALLERGY, GOAL, PREFERENCE -> true;
            case ILLNESS, TRAVEL, INJURY, STRESS, TRAINING, NUTRITION, GENERAL, MOOD, OTHER -> false;
        };

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("user_id", event.userId());
        metadata.put("date", event.date().toString());
        metadata.put("note_type", event.type().name());
        metadata.put("memory_type", isPermanent
                ? MemoryType.FACT.name()
                : MemoryType.EPISODIC.name());
        metadata.put("memory_horizon", isPermanent ? "LONG_TERM" : "SHORT_TERM");
        metadata.put("created_at", clock.instant().toString());
        metadata.put("source_type", "USER_NOTE");
        if (event.noteId() != null) {
            metadata.put("source_id", event.noteId());
        }

        if (!isPermanent) {
            metadata.put("expires_at",
                    clock.instant().plus(7, ChronoUnit.DAYS).toString());
        }

        String sourceKey = event.noteId() == null
                ? "note:%d:%s:%s:%s".formatted(event.userId(), event.date(), event.type(),
                Integer.toHexString(event.content().hashCode()))
                : noteMemoryId(event.userId(), event.noteId());
        String memoryId = stableVectorId(sourceKey);
        metadata.put("memory_id", sourceKey);
        metadata.put("vector_id", memoryId);
        vectorStore.delete(List.of(memoryId));
        vectorStore.add(List.of(new Document(memoryId, event.content(), metadata)));
    }

    @ApplicationModuleListener
    public void onUserNoteDeleted(UserNoteDeletedEvent event) {
        if (event.noteId() != null) {
            vectorStore.delete(List.of(stableVectorId(noteMemoryId(event.userId(), event.noteId()))));
        }
    }

    private String insightMemoryId(InsightGeneratedEvent event) {
        return insightMemoryId(event.userId(), event.insightType(), event.date());
    }

    private String insightMemoryId(Long userId, InsightType insightType, java.time.LocalDate date) {
        return "insight:%d:%s:%s".formatted(
                userId,
                insightType.name(),
                date
        );
    }

    private String noteMemoryId(Long userId, Long noteId) {
        return "note:%d:%d".formatted(userId, noteId);
    }

    private String stableVectorId(String sourceKey) {
        return java.util.UUID.nameUUIDFromBytes(sourceKey.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
