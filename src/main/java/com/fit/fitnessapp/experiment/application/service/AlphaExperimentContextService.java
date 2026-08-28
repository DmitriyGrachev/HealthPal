package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.api.evidence.EvidenceSourceQuery;
import com.fit.fitnessapp.api.evidence.EvidenceSourceRequest;
import com.fit.fitnessapp.api.evidence.EvidenceSourceSlice;
import com.fit.fitnessapp.experiment.application.port.out.EvidenceRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.GoalRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.InvestigationRepositoryPort;
import com.fit.fitnessapp.experiment.domain.AlphaExperimentContext;
import com.fit.fitnessapp.experiment.domain.DataCoverage;
import com.fit.fitnessapp.experiment.domain.EvidenceFreshness;
import com.fit.fitnessapp.experiment.domain.EvidencePurpose;
import com.fit.fitnessapp.experiment.domain.EvidenceRef;
import com.fit.fitnessapp.experiment.domain.EvidenceSourceType;
import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.Investigation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlphaExperimentContextService {

    private static final EnumSet<EvidenceSourceType> BASELINE_SOURCES = EnumSet.of(
            EvidenceSourceType.NUTRITION_DAY,
            EvidenceSourceType.WORKOUT_DAY);
    private static final EnumSet<EvidenceSourceType> INTERVENTION_SOURCES = EnumSet.of(
            EvidenceSourceType.MANUAL_CHECK_IN,
            EvidenceSourceType.NUTRITION_DAY,
            EvidenceSourceType.WORKOUT_DAY);
    private static final Comparator<EvidenceSourceType> SOURCE_ORDER = Comparator.comparing(Enum::name);
    private static final Comparator<EvidenceRef> REFERENCE_ORDER = Comparator
            .comparing((EvidenceRef reference) -> reference.sourceType().name())
            .thenComparing(EvidenceRef::sourceId)
            .thenComparingLong(EvidenceRef::sourceVersion)
            .thenComparing(EvidenceRef::contentHash)
            .thenComparing(EvidenceRef::observedAt);

    private final InvestigationRepositoryPort investigations;
    private final GoalRepositoryPort goals;
    private final ExperimentRepositoryPort experiments;
    private final EvidenceRepositoryPort evidence;
    private final List<EvidenceSourceQuery> sources;
    private final Clock clock;

    public AlphaExperimentContextService(
            InvestigationRepositoryPort investigations,
            GoalRepositoryPort goals,
            ExperimentRepositoryPort experiments,
            EvidenceRepositoryPort evidence,
            List<EvidenceSourceQuery> sources,
            Clock clock) {
        this.investigations = investigations;
        this.goals = goals;
        this.experiments = experiments;
        this.evidence = evidence;
        this.sources = List.copyOf(sources == null ? List.of() : sources);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional
    public AlphaExperimentContext assemble(Long userId, Long experimentId) {
        requirePositive(userId, "userId");
        requirePositive(experimentId, "experimentId");

        Experiment experiment = experiments.findExperimentByUserIdAndId(userId, experimentId)
                .orElseThrow(ExperimentNotFoundException::new);
        Investigation investigation = investigations.findByUserIdAndId(userId, experiment.investigationId())
                .orElseThrow(() -> new IllegalStateException("owned Investigation is missing"));
        Goal linkedGoal = goals.findGoalByUserIdAndId(userId, experiment.goalId())
                .orElseThrow(() -> new IllegalStateException("owned Goal is missing"));
        Goal activeGoal = linkedGoal.status() == GoalStatus.ACTIVE ? linkedGoal : null;

        Instant asOf = clock.instant();
        LocalDate interventionStart = experiment.baselineEndDate().plusDays(1);
        LocalDate interventionEnd = interventionStart.plusDays(experiment.durationDays() - 1L);

        List<PhaseEvidence> phases = List.of(
                collectPhase(userId, experimentId, EvidencePurpose.BASELINE,
                        experiment.baselineStartDate(), experiment.baselineEndDate(), BASELINE_SOURCES, asOf),
                collectPhase(userId, experimentId, EvidencePurpose.INTERVENTION,
                        interventionStart, interventionEnd, INTERVENTION_SOURCES, asOf));

        List<DataCoverage> coverage = new ArrayList<>();
        List<EvidenceFreshness> freshness = new ArrayList<>();
        List<String> missingFields = new ArrayList<>();
        if (activeGoal == null) {
            missingFields.add("ACTIVE_GOAL");
        }
        Map<ReferenceIdentity, EvidenceRef> uniqueReferences = new LinkedHashMap<>();
        Map<EvidenceWriteIdentity, EvidenceRepositoryPort.EvidenceRefWrite> uniqueWrites = new LinkedHashMap<>();

        for (PhaseEvidence phase : phases) {
            phase.bySource().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(SOURCE_ORDER))
                    .forEach(entry -> {
                        EvidenceSourceType sourceType = entry.getKey();
                        List<EvidenceSourceSlice.EvidenceItem> items = entry.getValue();
                        List<LocalDate> missingDates = dates(phase.fromInclusive(), phase.toInclusive()).stream()
                                .filter(date -> items.stream().noneMatch(item -> item.sourceDate().equals(date)))
                                .toList();
                        int expectedDays = Math.toIntExact(
                                ChronoUnit.DAYS.between(phase.fromInclusive(), phase.toInclusive()) + 1);
                        int observedDays = expectedDays - missingDates.size();
                        coverage.add(new DataCoverage(
                                phase.purpose(), sourceType, expectedDays, observedDays, missingDates));

                        Instant latest = items.stream()
                                .map(EvidenceSourceSlice.EvidenceItem::observedAt)
                                .max(Comparator.naturalOrder())
                                .orElse(null);
                        freshness.add(new EvidenceFreshness(
                                phase.purpose(), sourceType, latest,
                                latest == null ? null : Duration.between(latest, asOf).toDays()));
                        missingDates.forEach(date -> missingFields.add(
                                phase.purpose().name() + ":" + sourceType.name() + ":" + date));

                        items.forEach(item -> {
                            EvidenceRef reference = new EvidenceRef(
                                    sourceType, item.sourceId(), item.sourceVersion(),
                                    item.contentHash(), item.observedAt());
                            uniqueReferences.putIfAbsent(ReferenceIdentity.from(reference), reference);
                            EvidenceRepositoryPort.EvidenceRefWrite write =
                                    new EvidenceRepositoryPort.EvidenceRefWrite(
                                            phase.purpose(), item.sourceDate(), reference);
                            uniqueWrites.putIfAbsent(EvidenceWriteIdentity.from(write), write);
                        });
                    });
        }

        List<EvidenceRef> references = uniqueReferences.values().stream().sorted(REFERENCE_ORDER).toList();
        evidence.saveEvidenceRefs(userId, experimentId, List.copyOf(uniqueWrites.values()));
        return new AlphaExperimentContext(
                investigation,
                activeGoal,
                experiment,
                coverage,
                freshness,
                references,
                missingFields);
    }

    private PhaseEvidence collectPhase(
            Long userId,
            Long experimentId,
            EvidencePurpose purpose,
            LocalDate fromInclusive,
            LocalDate toInclusive,
            EnumSet<EvidenceSourceType> permittedSources,
            Instant asOf) {
        EvidenceSourceRequest request = new EvidenceSourceRequest(
                userId, experimentId, fromInclusive, toInclusive);
        Map<EvidenceSourceType, Map<ReferenceIdentity, EvidenceSourceSlice.EvidenceItem>> merged =
                new EnumMap<>(EvidenceSourceType.class);
        for (EvidenceSourceQuery source : sources) {
            EvidenceSourceSlice slice = source.query(request);
            if (slice == null) {
                throw new IllegalArgumentException("evidence source returned no slice");
            }
            EvidenceSourceType sourceType;
            try {
                sourceType = EvidenceSourceType.valueOf(slice.sourceType());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("unsupported evidence source type", exception);
            }
            if (!permittedSources.contains(sourceType)) {
                continue;
            }
            Map<ReferenceIdentity, EvidenceSourceSlice.EvidenceItem> items =
                    merged.computeIfAbsent(sourceType, ignored -> new LinkedHashMap<>());
            for (EvidenceSourceSlice.EvidenceItem item : slice.items()) {
                if (item.sourceDate().isBefore(fromInclusive) || item.sourceDate().isAfter(toInclusive)) {
                    throw new IllegalArgumentException("evidence item is outside the requested window");
                }
                if (item.observedAt().isAfter(asOf)) {
                    throw new IllegalArgumentException("evidence item is observed in the future");
                }
                EvidenceRef reference = new EvidenceRef(
                        sourceType, item.sourceId(), item.sourceVersion(), item.contentHash(), item.observedAt());
                items.putIfAbsent(ReferenceIdentity.from(reference), item);
            }
        }

        Map<EvidenceSourceType, List<EvidenceSourceSlice.EvidenceItem>> bySource =
                new EnumMap<>(EvidenceSourceType.class);
        merged.forEach((sourceType, items) -> bySource.put(sourceType, items.values().stream()
                .sorted(Comparator.comparing(EvidenceSourceSlice.EvidenceItem::sourceDate)
                        .thenComparing(EvidenceSourceSlice.EvidenceItem::sourceId)
                        .thenComparingLong(EvidenceSourceSlice.EvidenceItem::sourceVersion))
                .toList()));
        return new PhaseEvidence(purpose, fromInclusive, toInclusive, bySource);
    }

    private static List<LocalDate> dates(LocalDate fromInclusive, LocalDate toInclusive) {
        return fromInclusive.datesUntil(toInclusive.plusDays(1)).toList();
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private record PhaseEvidence(
            EvidencePurpose purpose,
            LocalDate fromInclusive,
            LocalDate toInclusive,
            Map<EvidenceSourceType, List<EvidenceSourceSlice.EvidenceItem>> bySource) {
    }

    private record ReferenceIdentity(
            EvidenceSourceType sourceType,
            String sourceId,
            long sourceVersion,
            String contentHash) {

        private static ReferenceIdentity from(EvidenceRef reference) {
            return new ReferenceIdentity(reference.sourceType(), reference.sourceId(),
                    reference.sourceVersion(), reference.contentHash());
        }
    }

    private record EvidenceWriteIdentity(EvidencePurpose purpose, ReferenceIdentity reference) {

        private static EvidenceWriteIdentity from(EvidenceRepositoryPort.EvidenceRefWrite write) {
            return new EvidenceWriteIdentity(write.purpose(), ReferenceIdentity.from(write.reference()));
        }
    }
}
