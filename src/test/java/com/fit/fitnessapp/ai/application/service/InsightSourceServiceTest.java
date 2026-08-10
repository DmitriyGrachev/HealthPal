package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.ai.AiInsightRepository;
import com.fit.fitnessapp.api.InsightType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightSourceServiceTest {

    @Mock
    private AiInsightRepository insightRepository;

    @Mock
    private InsightSourceLock insightSourceLock;

    @Test
    void matchesOnlyTheCurrentSourceSnapshot() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        AiInsightEntity current = AiInsightEntity.builder()
                .metadata(Map.of("snapshot_hash", "snapshot-b"))
                .build();
        when(insightRepository.findSourceForUpdate(42L, date, InsightType.DAILY))
                .thenReturn(Optional.of(current));
        InsightSourceService service = new InsightSourceService(insightRepository, insightSourceLock);

        assertThat(service.insightMatchesSnapshot(
                42L, InsightType.DAILY, date, "snapshot-b")).isTrue();
        assertThat(service.insightMatchesSnapshot(
                42L, InsightType.DAILY, date, "snapshot-a")).isFalse();
    }

    @Test
    void missingCurrentSourceVersionFailsClosed() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        AiInsightEntity legacy = AiInsightEntity.builder().metadata(Map.of()).build();
        when(insightRepository.findSourceForUpdate(42L, date, InsightType.DAILY))
                .thenReturn(Optional.of(legacy));
        InsightSourceService service = new InsightSourceService(insightRepository, insightSourceLock);

        assertThat(service.insightMatchesSnapshot(
                42L, InsightType.DAILY, date, "snapshot-b")).isFalse();
    }

    @Test
    void missingEventVersionFailsClosedWithoutRepositoryAccess() {
        InsightSourceService service = new InsightSourceService(insightRepository, insightSourceLock);

        assertThat(service.insightMatchesSnapshot(
                42L, InsightType.DAILY, LocalDate.of(2026, 7, 6), "  ")).isFalse();

        verifyNoInteractions(insightRepository, insightSourceLock);
    }

    @Test
    void sourceExistenceUsesTheSameOwnedRepositoryLookup() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(insightRepository.findSourceForUpdate(42L, date, InsightType.DAILY))
                .thenReturn(Optional.empty());
        InsightSourceService service = new InsightSourceService(insightRepository, insightSourceLock);

        assertThat(service.insightExists(42L, InsightType.DAILY, date)).isFalse();
    }
}
