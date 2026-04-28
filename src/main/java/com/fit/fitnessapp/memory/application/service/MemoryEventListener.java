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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryEventListener {

    private final VectorStore vectorStore;

    @ApplicationModuleListener
    public void onInsightGenerated(InsightGeneratedEvent event) {

        // Ежедневные инсайты — краткосрочные (14 дней)
        // Недельные/месячные — среднесрочные (90 дней)
        Instant expiresAt = switch (event.insightType()) {
            case DAILY   -> Instant.now().plus(14, ChronoUnit.DAYS);
            case WEEKLY  -> Instant.now().plus(90, ChronoUnit.DAYS);
            case MONTHLY -> null; // не истекает
        };

        String horizon = switch (event.insightType()) {
            case DAILY   -> "SHORT_TERM";
            case WEEKLY  -> "MID_TERM";
            case MONTHLY -> "LONG_TERM";
        };

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("user_id", event.userId());
        metadata.put("date", event.date().toString());
        metadata.put("insight_type", event.insightType().name());
        metadata.put("memory_type", MemoryType.SEMANTIC.name());
        metadata.put("memory_horizon", horizon);
        metadata.put("created_at", Instant.now().toString());
        if (expiresAt != null) {
            metadata.put("expires_at", expiresAt.toString());
        }

        vectorStore.add(List.of(new Document(event.content(), metadata)));
    }

    @ApplicationModuleListener
    public void onUserNoteCreated(UserNoteCreatedEvent event) {

        // Временные заметки (болезнь, событие) — 7 дней
        // Постоянные (аллергия, цель) — никогда не истекают
        boolean isPermanent = switch (event.type()) {
            case ALLERGY, GOAL, PREFERENCE -> true;
            case ILLNESS, TRAVEL,INJURY,STRESS, OTHER -> false;
        };

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("user_id", event.userId());
        metadata.put("date", event.date().toString());
        metadata.put("note_type", event.type().name());
        metadata.put("memory_type", isPermanent
                ? MemoryType.FACT.name()
                : MemoryType.EPISODIC.name());
        metadata.put("memory_horizon", isPermanent ? "LONG_TERM" : "SHORT_TERM");
        metadata.put("created_at", Instant.now().toString());

        if (!isPermanent) {
            metadata.put("expires_at",
                    Instant.now().plus(7, ChronoUnit.DAYS).toString());
        }

        vectorStore.add(List.of(new Document(event.content(), metadata)));
    }
}
