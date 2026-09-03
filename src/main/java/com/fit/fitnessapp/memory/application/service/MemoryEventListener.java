package com.fit.fitnessapp.memory.application.service;

import com.fit.fitnessapp.api.InsightDeletedEvent;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightSourceApi;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.api.SensitiveAiEgressGuard;
import com.fit.fitnessapp.auth.api.UserDataPresenceApi;
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
    private final UserDataPresenceApi userDataPresenceApi;
    private final InsightSourceApi insightSourceApi;
    private final SensitiveAiEgressGuard egressGuard;

    @Autowired
    public MemoryEventListener(
            VectorStore vectorStore,
            Clock clock,
            UserDataPresenceApi userDataPresenceApi,
            InsightSourceApi insightSourceApi,
            SensitiveAiEgressGuard egressGuard) {
        this.vectorStore = vectorStore;
        this.clock = clock;
        this.userDataPresenceApi = userDataPresenceApi;
        this.insightSourceApi = insightSourceApi;
        this.egressGuard = egressGuard;
    }

    @ApplicationModuleListener
    public void onInsightGenerated(InsightGeneratedEvent event) {
        if (!userDataPresenceApi.userExists(event.userId())) {
            logSkippedInsight(event.userId(), event.insightType(), event.date(), "INSIGHT_USER_MISSING");
            return;
        }
        if (event.snapshotHash() == null || event.snapshotHash().isBlank()) {
            logSkippedInsight(event.userId(), event.insightType(), event.date(), "INSIGHT_VERSION_MISSING");
            return;
        }
        if (!insightSourceApi.insightMatchesSnapshot(
                event.userId(), event.insightType(), event.date(), event.snapshotHash())) {
            logSkippedInsight(event.userId(), event.insightType(), event.date(), "INSIGHT_VERSION_STALE");
            return;
        }

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
        metadata.put("snapshot_hash", event.snapshotHash());
        if (expiresAt != null) {
            metadata.put("expires_at", expiresAt.toString());
        }

        egressGuard.validateSensitiveEgress();
        vectorStore.delete(List.of(memoryId));
        vectorStore.add(List.of(new Document(memoryId, event.content(), metadata)));
    }

    @ApplicationModuleListener
    public void onInsightDeleted(InsightDeletedEvent event) {
        if (!userDataPresenceApi.userExists(event.userId())) {
            logSkippedInsight(event.userId(), event.insightType(), event.date(), "INSIGHT_USER_MISSING");
            return;
        }
        if (insightSourceApi.insightExists(event.userId(), event.insightType(), event.date())) {
            logSkippedInsight(event.userId(), event.insightType(), event.date(), "INSIGHT_SOURCE_RECREATED");
            return;
        }
        vectorStore.delete(List.of(stableVectorId(
                insightMemoryId(event.userId(), event.insightType(), event.date()))));
    }

    @ApplicationModuleListener
    public void onUserNoteCreated(UserNoteCreatedEvent event) {
        if (!userDataPresenceApi.userExists(event.userId())
                || !userDataPresenceApi.userNoteExists(event.userId(), event.noteId())) {
            log.info("Skipping stale User Note memory event userId={} noteId={}",
                    event.userId(), event.noteId());
            return;
        }

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
        metadata.put("memory_type", switch (event.type()) {
            case GOAL -> MemoryType.EPISODIC.name();
            default -> isPermanent ? MemoryType.FACT.name() : MemoryType.EPISODIC.name();
        });
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

        String sourceKey = noteMemoryId(event.userId(), event.noteId());
        String memoryId = stableVectorId(sourceKey);
        metadata.put("memory_id", sourceKey);
        metadata.put("vector_id", memoryId);
        egressGuard.validateSensitiveEgress();
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

    private void logSkippedInsight(
            Long userId,
            InsightType insightType,
            java.time.LocalDate date,
            String reasonCode) {
        log.info("Skipping insight memory replay userId={} insightType={} date={} reasonCode={}",
                userId, insightType, date, reasonCode);
    }
}
