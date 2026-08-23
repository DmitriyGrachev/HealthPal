package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.api.ChangeType;
import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.api.NutritionSyncedEvent;
import com.fit.fitnessapp.api.UserDateTransactionLock;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionSourceStatePort;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySaveResult;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import com.fit.fitnessapp.nutrition.domain.NutritionMonthSaveResult;
import com.fit.fitnessapp.nutrition.domain.NutritionSyncCommitResult;
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

/**
 * The only transactional boundary for nutrition canonical/source publication.
 * Provider access belongs to {@link NutritionService} and happens before this
 * service is called.
 */
@Service
public class NutritionSyncCommitService {

    private final NutritionCommandPort commandPort;
    private final NutritionSourceStatePort sourceStatePort;
    private final ApplicationEventPublisher eventPublisher;
    private final UserDateTransactionLock userDateTransactionLock;

    @Autowired
    public NutritionSyncCommitService(
            NutritionCommandPort commandPort,
            NutritionSourceStatePort sourceStatePort,
            ApplicationEventPublisher eventPublisher,
            UserDateTransactionLock userDateTransactionLock) {
        this.commandPort = commandPort;
        this.sourceStatePort = sourceStatePort;
        this.eventPublisher = eventPublisher;
        this.userDateTransactionLock = userDateTransactionLock;
    }

    /** Compatibility constructor for narrow legacy unit tests; production uses the source-state port. */
    public NutritionSyncCommitService(
            NutritionCommandPort commandPort,
            ApplicationEventPublisher eventPublisher) {
        this(commandPort, null, eventPublisher, null);
    }

    @Transactional
    public NutritionSyncCommitResult commitDay(NutritionDay day) {
        if (!lockDate(day.userId(), day.date())) {
            return new NutritionSyncCommitResult(List.of());
        }
        NutritionDaySaveResult saved = commandPort.saveNutritionDay(day);
        if (!saved.changed()) {
            return new NutritionSyncCommitResult(List.of());
        }
        if (sourceStatePort == null) {
            eventPublisher.publishEvent(legacyEvent(saved));
            return NutritionSyncCommitResult.fromDates(List.of(saved.date()));
        }
        String contentHash = canonicalHash(saved.summaryHash(), saved.entriesHash());
        Optional<DomainSourceState> state = sourceStatePort.advance(
                saved.userId(), saved.date(), ChangeType.UPSERT, contentHash);
        state.ifPresent(this::publish);
        return new NutritionSyncCommitResult(state.stream().toList());
    }

    @Transactional
    public NutritionSyncCommitResult commitMonth(NutritionMonth month) {
        LocalDate firstDate = month.days().stream().map(NutritionDaySummary::date).min(LocalDate::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("nutrition month must contain a date window"));
        return commitMonth(month, firstDate.withDayOfMonth(1), firstDate.withDayOfMonth(firstDate.lengthOfMonth()));
    }

    @Transactional
    public NutritionSyncCommitResult commitMonth(
            NutritionMonth month, LocalDate monthStart, LocalDate monthEnd) {
        for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
            if (!lockDate(month.userId(), date)) {
                return new NutritionSyncCommitResult(List.of());
            }
        }
        NutritionMonthSaveResult savedMonth = commandPort.saveNutritionMonth(month);
        List<NutritionDaySaveResult> deletedDays = commandPort.deleteNutritionDaysMissingFromMonth(
                month.userId(), monthStart, monthEnd,
                month.days().stream().map(NutritionDaySummary::date).collect(java.util.stream.Collectors.toSet()));

        if (sourceStatePort == null) {
            Map<LocalDate, NutritionDaySummary> summaries = month.days().stream()
                    .collect(java.util.stream.Collectors.toMap(NutritionDaySummary::date, value -> value));
            savedMonth.changedDates().stream().map(summaries::get).filter(java.util.Objects::nonNull)
                    .map(this::legacyEvent).forEach(eventPublisher::publishEvent);
            deletedDays.stream().filter(NutritionDaySaveResult::changed)
                    .map(this::legacyEvent).forEach(eventPublisher::publishEvent);
            List<LocalDate> changedDates = new ArrayList<>(savedMonth.changedDates());
            deletedDays.stream().filter(NutritionDaySaveResult::changed).map(NutritionDaySaveResult::date)
                    .forEach(changedDates::add);
            return NutritionSyncCommitResult.fromDates(changedDates.stream().distinct().sorted().toList());
        }

        Map<LocalDate, NutritionDaySaveResult> changedByDate = new LinkedHashMap<>();
        savedMonth.changedDays().forEach(day -> changedByDate.put(day.date(), day));
        Map<LocalDate, NutritionDaySummary> summariesByDate = month.days().stream()
                .collect(java.util.stream.Collectors.toMap(NutritionDaySummary::date, value -> value));
        List<PendingChange> changes = new ArrayList<>();
        for (LocalDate date : savedMonth.changedDates()) {
            NutritionDaySaveResult result = changedByDate.get(date);
            String hash = result == null
                    ? canonicalSummaryHash(summariesByDate.get(date))
                    : canonicalHash(result.summaryHash(), result.entriesHash());
            changes.add(new PendingChange(date, ChangeType.UPSERT, hash));
        }
        for (NutritionDaySaveResult deleted : deletedDays) {
            if (deleted.changed()) {
                changes.add(new PendingChange(deleted.date(), ChangeType.DELETE,
                        canonicalHash(deleted.summaryHash(), deleted.entriesHash())));
            }
        }

        List<DomainSourceState> states = new ArrayList<>();
        changes.stream().distinct().sorted(Comparator.comparing(PendingChange::date)).forEach(change ->
                sourceStatePort.advance(month.userId(), change.date(), change.changeType(), change.contentHash())
                        .ifPresent(state -> {
                            states.add(state);
                            publish(state);
                        }));
        return new NutritionSyncCommitResult(states);
    }

    private void publish(DomainSourceState state) {
        eventPublisher.publishEvent(NutritionSyncedEvent.forSourceState(state));
    }

    private boolean lockDate(Long userId, LocalDate date) {
        return userDateTransactionLock == null
                || userDateTransactionLock.lockAndReadLifecycleEpoch(userId, date).isPresent();
    }

    private NutritionSyncedEvent legacyEvent(NutritionDaySaveResult result) {
        return new NutritionSyncedEvent(result.userId(), result.date(), result.totalCalories(), result.protein(),
                result.fat(), result.carbohydrate(), true, result.summaryHash(), result.entriesHash());
    }

    private NutritionSyncedEvent legacyEvent(NutritionDaySummary summary) {
        return new NutritionSyncedEvent(summary.userId(), summary.date(), (int) summary.calories(), summary.protein(),
                summary.fat(), summary.carbohydrate(), true, canonicalSummaryHash(summary),
                canonicalSummaryHash(summary));
    }

    private String canonicalSummaryHash(NutritionDaySummary summary) {
        if (summary == null) {
            throw new IllegalArgumentException("changed nutrition date is missing from monthly snapshot");
        }
        return canonicalHash(
                Double.toString(summary.calories()),
                Double.toString(summary.protein()) + "|" + summary.fat() + "|" + summary.carbohydrate());
    }

    private String canonicalHash(String summaryHash, String entriesHash) {
        String payload = "NUTRITION_DAY|" + String.valueOf(summaryHash) + "|" + String.valueOf(entriesHash);
        return DigestUtils.sha256Hex(payload.getBytes(StandardCharsets.UTF_8));
    }

    private record PendingChange(LocalDate date, ChangeType changeType, String contentHash) {
    }
}
