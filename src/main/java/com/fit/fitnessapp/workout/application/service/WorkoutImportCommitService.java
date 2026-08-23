package com.fit.fitnessapp.workout.application.service;

import com.fit.fitnessapp.api.ChangeType;
import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.api.WorkoutImportedEvent;
import com.fit.fitnessapp.api.UserDateTransactionLock;
import com.fit.fitnessapp.workout.application.port.out.WorkoutPersistencePort;
import com.fit.fitnessapp.workout.application.port.out.WorkoutSourceStatePort;
import com.fit.fitnessapp.workout.domain.CardioExercise;
import com.fit.fitnessapp.workout.domain.Exercise;
import com.fit.fitnessapp.workout.domain.Set;
import com.fit.fitnessapp.workout.domain.WorkoutImportCommitResult;
import com.fit.fitnessapp.workout.domain.WorkoutImportResult;
import com.fit.fitnessapp.workout.domain.WorkoutCanonicalDay;
import com.fit.fitnessapp.workout.domain.WorkoutPersistenceResult;
import com.fit.fitnessapp.workout.domain.WorkoutSession;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The only transactional boundary for workout canonical/source publication.
 * CSV parsing remains in {@link WorkoutImportService}, outside this boundary.
 */
@Service
public class WorkoutImportCommitService {

    private final WorkoutPersistencePort persistencePort;
    private final WorkoutSourceStatePort sourceStatePort;
    private final ApplicationEventPublisher eventPublisher;
    private final UserDateTransactionLock userDateTransactionLock;

    @Autowired
    public WorkoutImportCommitService(
            WorkoutPersistencePort persistencePort,
            WorkoutSourceStatePort sourceStatePort,
            ApplicationEventPublisher eventPublisher,
            UserDateTransactionLock userDateTransactionLock) {
        this.persistencePort = persistencePort;
        this.sourceStatePort = sourceStatePort;
        this.eventPublisher = eventPublisher;
        this.userDateTransactionLock = userDateTransactionLock;
    }

    /** Compatibility constructor for narrow legacy unit tests; production uses the source-state port. */
    public WorkoutImportCommitService(
            WorkoutPersistencePort persistencePort,
            ApplicationEventPublisher eventPublisher) {
        this(persistencePort, null, eventPublisher, null);
    }

    @Transactional
    public WorkoutImportCommitResult commit(WorkoutImportResult parsed, Long userId) {
        List<LocalDate> affectedDates = new ArrayList<>();
        parsed.sessions().stream()
                .map(WorkoutSession::date)
                .filter(java.util.Objects::nonNull)
                .map(java.time.LocalDateTime::toLocalDate)
                .forEach(affectedDates::add);
        List<LocalDate> persistedDates = persistencePort.findAffectedDates(parsed.sessions(), userId);
        if (persistedDates != null) {
            affectedDates.addAll(persistedDates);
        }
        for (LocalDate date : affectedDates.stream().distinct().sorted().toList()) {
            if (userDateTransactionLock != null
                    && userDateTransactionLock.lockAndReadLifecycleEpoch(userId, date).isEmpty()) {
                return new WorkoutImportCommitResult(List.of());
            }
        }
        WorkoutPersistenceResult persisted = persistencePort.saveAll(parsed.sessions(), userId);
        if (persisted.changedDates().isEmpty()) {
            return new WorkoutImportCommitResult(List.of());
        }
        if (sourceStatePort == null) {
            publishLegacy(userId, parsed, persisted.changedDates());
            return WorkoutImportCommitResult.fromDates(persisted.changedDates());
        }

        Map<LocalDate, String> hashes = canonicalHashesByDate(parsed.sessions());
        Map<LocalDate, WorkoutCanonicalDay> canonicalDays = persisted.canonicalDays().stream()
                .collect(Collectors.toMap(WorkoutCanonicalDay::date, value -> value));
        List<DomainSourceState> states = new ArrayList<>();
        persisted.changedDates().stream().distinct().sorted().forEach(date -> {
            WorkoutCanonicalDay canonicalDay = canonicalDays.get(date);
            ChangeType changeType = canonicalDay == null || canonicalDay.present()
                    ? ChangeType.UPSERT
                    : ChangeType.DELETE;
            String hash = canonicalDay == null
                    ? hashes.getOrDefault(date, hashForCanonicalDate(List.of()))
                    : canonicalDay.contentHash();
            Optional<DomainSourceState> state = sourceStatePort.advance(userId, date, changeType, hash);
            state.ifPresent(value -> {
                states.add(value);
                eventPublisher.publishEvent(WorkoutImportedEvent.forSourceState(value));
            });
        });
        return new WorkoutImportCommitResult(states);
    }

    @Transactional
    public WorkoutImportCommitResult commit(List<WorkoutSession> sessions, Long userId) {
        return commit(WorkoutImportResult.from(sessions, List.of()), userId);
    }

    private void publishLegacy(Long userId, WorkoutImportResult parsed, List<LocalDate> dates) {
        List<LocalDate> affected = dates.stream().distinct().sorted().toList();
        if (affected.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new WorkoutImportedEvent(
                userId, affected.getFirst(), affected.getLast(), parsed.importedCount(),
                parsed.warnings().size(), affected));
    }

    private Map<LocalDate, String> canonicalHashesByDate(List<WorkoutSession> sessions) {
        Map<LocalDate, List<WorkoutSession>> byDate = sessions.stream()
                .filter(session -> session.date() != null)
                .collect(Collectors.groupingBy(session -> session.date().toLocalDate(), LinkedHashMap::new,
                        Collectors.toList()));
        Map<LocalDate, String> hashes = new LinkedHashMap<>();
        byDate.forEach((date, dateSessions) -> hashes.put(date, hashForCanonicalDate(dateSessions)));
        return hashes;
    }

    private String hashForCanonicalDate(List<WorkoutSession> sessions) {
        StringBuilder canonical = new StringBuilder("WORKOUT_DAY|");
        sessions.stream()
                .sorted(Comparator.comparing(WorkoutSession::externalId,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(session -> {
                    canonical.append("S:").append(value(session.externalId())).append('|');
                    session.exercises().stream()
                            .sorted(Comparator.comparing(Exercise::jefitLogId,
                                    Comparator.nullsFirst(Comparator.naturalOrder())))
                            .forEach(exercise -> {
                                canonical.append("E:").append(value(exercise.jefitLogId())).append('|');
                                exercise.sets().stream()
                                        .sorted(Comparator.comparingInt(Set::setIndex)
                                                .thenComparingInt(Set::reps)
                                                .thenComparingDouble(Set::weightKg))
                                        .forEach(set -> canonical.append("T:")
                                                .append(set.setIndex()).append(':').append(set.reps()).append(':')
                                                .append(Double.toString(set.weightKg())).append('|'));
                            });
                    session.cardioExercises().stream()
                            .sorted(Comparator.comparing(CardioExercise::jefitId,
                                    Comparator.nullsFirst(Comparator.naturalOrder())))
                            .forEach(cardio -> canonical.append("C:")
                                    .append(value(cardio.jefitId())).append(':')
                                    .append(value(cardio.exerciseId())).append(':')
                                    .append(cardio.durationSeconds()).append(':')
                                    .append(Double.toString(cardio.distance())).append(':')
                                    .append(Double.toString(cardio.calories())).append('|'));
                });
        return DigestUtils.sha256Hex(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String value(Object value) {
        return value == null ? "null" : value.toString();
    }
}
