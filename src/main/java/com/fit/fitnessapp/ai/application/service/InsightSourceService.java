package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.ai.AiInsightRepository;
import com.fit.fitnessapp.api.InsightSourceApi;
import com.fit.fitnessapp.api.InsightType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InsightSourceService implements InsightSourceApi {

    private static final String SNAPSHOT_HASH = "snapshot_hash";

    private final AiInsightRepository insightRepository;
    private final InsightSourceLock insightSourceLock;

    @Override
    public boolean insightExists(Long userId, InsightType insightType, LocalDate date) {
        insightSourceLock.lock(userId, insightType, date);
        return insightRepository.findSourceForUpdate(userId, date, insightType).isPresent();
    }

    @Override
    public boolean insightMatchesSnapshot(
            Long userId,
            InsightType insightType,
            LocalDate date,
            String snapshotHash) {
        if (snapshotHash == null || snapshotHash.isBlank()) {
            return false;
        }
        insightSourceLock.lock(userId, insightType, date);
        return insightRepository.findSourceForUpdate(userId, date, insightType)
                .map(AiInsightEntity::getMetadata)
                .map(this::snapshotHash)
                .map(snapshotHash::equals)
                .orElse(false);
    }

    private String snapshotHash(Map<String, Object> metadata) {
        Object value = metadata == null ? null : metadata.get(SNAPSHOT_HASH);
        return value == null ? null : value.toString();
    }
}
