package com.fit.fitnessapp.api.evidence;

import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.experiment.adapter.out.persistence.ExperimentCheckInEvidenceSourceQuery;
import com.fit.fitnessapp.experiment.application.port.out.EvidenceRepositoryPort;
import com.fit.fitnessapp.experiment.domain.AdherenceStatus;
import com.fit.fitnessapp.experiment.domain.CheckInSource;
import com.fit.fitnessapp.experiment.domain.ContextRating;
import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;
import com.fit.fitnessapp.nutrition.adapter.out.evidence.NutritionEvidenceSourceQuery;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionSourceStateQueryPort;
import com.fit.fitnessapp.workout.adapter.out.evidence.WorkoutEvidenceSourceQuery;
import com.fit.fitnessapp.workout.application.port.in.WorkoutSourceStateQueryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceSourceContributorTest {

    private static final long USER_ID = 17L;
    private static final long SUBJECT_ID = 27L;
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 8, 1);
    private static final LocalDate SECOND_DAY = FIRST_DAY.plusDays(1);
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-02T12:00:00Z");

    @Test
    void nutritionContributorReturnsOnlyPresentOwnerScopedSourceVersions() {
        NutritionSourceStateQueryPort states = mock(NutritionSourceStateQueryPort.class);
        when(states.findCurrent(USER_ID, FIRST_DAY)).thenReturn(Optional.of(
                state("NUTRITION_DAY", FIRST_DAY, 3, true, 'a')));
        when(states.findCurrent(USER_ID, SECOND_DAY)).thenReturn(Optional.of(
                state("NUTRITION_DAY", SECOND_DAY, 4, false, 'b')));

        EvidenceSourceSlice slice = new NutritionEvidenceSourceQuery(states)
                .query(new EvidenceSourceRequest(USER_ID, SUBJECT_ID, FIRST_DAY, SECOND_DAY));

        assertThat(slice.sourceType()).isEqualTo("NUTRITION_DAY");
        assertThat(slice.items()).containsExactly(new EvidenceSourceSlice.EvidenceItem(
                FIRST_DAY.toString(), 3, String.valueOf('a').repeat(64), FIRST_DAY, OBSERVED_AT));
    }

    @Test
    void workoutContributorReturnsOnlyPresentOwnerScopedSourceVersions() {
        WorkoutSourceStateQueryPort states = mock(WorkoutSourceStateQueryPort.class);
        when(states.findCurrent(USER_ID, FIRST_DAY)).thenReturn(Optional.empty());
        when(states.findCurrent(USER_ID, SECOND_DAY)).thenReturn(Optional.of(
                state("WORKOUT_DAY", SECOND_DAY, 8, true, 'c')));

        EvidenceSourceSlice slice = new WorkoutEvidenceSourceQuery(states)
                .query(new EvidenceSourceRequest(USER_ID, SUBJECT_ID, FIRST_DAY, SECOND_DAY));

        assertThat(slice.sourceType()).isEqualTo("WORKOUT_DAY");
        assertThat(slice.items()).containsExactly(new EvidenceSourceSlice.EvidenceItem(
                SECOND_DAY.toString(), 8, String.valueOf('c').repeat(64), SECOND_DAY, OBSERVED_AT));
    }

    @Test
    void checkInContributorProducesStableVersionedHashWithoutExposingContent() {
        EvidenceRepositoryPort evidence = mock(EvidenceRepositoryPort.class);
        ExperimentCheckIn checkIn = new ExperimentCheckIn(
                91L, USER_ID, SUBJECT_ID, SECOND_DAY, ZoneId.of("Europe/Chisinau"),
                Instant.parse("2026-08-02T08:00:00Z"), Instant.parse("2026-08-02T09:00:00Z"),
                AdherenceStatus.PARTIAL, new BigDecimal("0.75"), "travel", "private note",
                new ContextRating(8), new ContextRating(6), new ContextRating(7),
                CheckInSource.MANUAL, OBSERVED_AT, OBSERVED_AT);
        when(evidence.findCheckInsByUserIdAndExperimentIdAndLocalDateBetween(
                USER_ID, SUBJECT_ID, FIRST_DAY, SECOND_DAY)).thenReturn(List.of(checkIn));
        ExperimentCheckInEvidenceSourceQuery contributor = new ExperimentCheckInEvidenceSourceQuery(evidence);
        EvidenceSourceRequest request = new EvidenceSourceRequest(
                USER_ID, SUBJECT_ID, FIRST_DAY, SECOND_DAY);

        EvidenceSourceSlice first = contributor.query(request);
        EvidenceSourceSlice replay = contributor.query(request);

        assertThat(first).isEqualTo(replay);
        assertThat(first.sourceType()).isEqualTo("MANUAL_CHECK_IN");
        assertThat(first.items()).singleElement().satisfies(item -> {
            assertThat(item.sourceId()).isEqualTo("91");
            assertThat(item.sourceVersion()).isOne();
            assertThat(item.contentHash()).matches("[0-9a-f]{64}");
            assertThat(item.contentHash()).doesNotContain("private", "travel");
            assertThat(item.sourceDate()).isEqualTo(SECOND_DAY);
            assertThat(item.observedAt()).isEqualTo(OBSERVED_AT);
        });
    }

    private static DomainSourceState state(
            String sourceType,
            LocalDate sourceDate,
            long sourceVersion,
            boolean present,
            char hashCharacter) {
        return new DomainSourceState(
                USER_ID,
                sourceType,
                sourceDate,
                sourceVersion,
                present,
                String.valueOf(hashCharacter).repeat(64),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                1,
                OBSERVED_AT.minusSeconds(60),
                OBSERVED_AT);
    }
}
