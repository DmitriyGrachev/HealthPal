package com.fit.fitnessapp.nutrition.adapter.out.persistence;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatSecretConnectionJpaEntity;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatsecretFoodEntry;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatsecretJpaDay;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatSecretConnectionJpaRepository;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatsecretDayJpaRepository;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatsecretFoodEntryJpaRepository;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySaveResult;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import com.fit.fitnessapp.nutrition.domain.NutritionMonthSaveResult;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NutritionPersistenceAdapter implements NutritionCommandPort {

    private final FatSecretConnectionJpaRepository connectionRepository;
    private final FatsecretDayJpaRepository dayRepository;
    private final FatsecretFoodEntryJpaRepository foodEntryRepository;
    private final FatSecretTokenCipher tokenCipher;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public NutritionPersistenceAdapter(FatSecretConnectionJpaRepository connectionRepository,
                                       FatsecretDayJpaRepository dayRepository,
                                       FatsecretFoodEntryJpaRepository foodEntryRepository,
                                       FatSecretTokenCipher tokenCipher, Clock clock) {
        this.connectionRepository = connectionRepository;
        this.dayRepository = dayRepository;
        this.foodEntryRepository = foodEntryRepository;
        this.tokenCipher = tokenCipher;
        this.clock = clock;
    }

    public NutritionPersistenceAdapter(FatSecretConnectionJpaRepository connectionRepository,
                                       FatsecretDayJpaRepository dayRepository,
                                       FatsecretFoodEntryJpaRepository foodEntryRepository,
                                       FatSecretTokenCipher tokenCipher) {
        this(connectionRepository, dayRepository, foodEntryRepository, tokenCipher, Clock.systemUTC());
    }

    @Override
    public void saveToken(Long userId, FatSecretToken token) {
        FatSecretConnectionJpaEntity entity = connectionRepository.findByUserId(userId)
                .orElseGet(() -> {
                    FatSecretConnectionJpaEntity newEntity = new FatSecretConnectionJpaEntity();
                    newEntity.setUserId(userId);
                    return newEntity;
                });

        entity.setAccessToken(tokenCipher.encrypt(token.accessToken()));
        entity.setAccessTokenSecret(tokenCipher.encrypt(token.accessTokenSecret()));

        connectionRepository.save(entity);
    }

    @Override
    public Optional<FatSecretToken> getToken(Long userId) {
        return connectionRepository.findByUserId(userId)
                .map(entity -> new FatSecretToken(
                        tokenCipher.decrypt(entity.getAccessToken()),
                        tokenCipher.decrypt(entity.getAccessTokenSecret())
                ));
    }

    @Override
    public List<Long> getAllConnectedUserIds() {
        return connectionRepository.findAll()
                .stream()
                .map(FatSecretConnectionJpaEntity::getUserId)
                .toList();
    }

    /**
     * Idempotent full-day upsert: updates aggregates and syncs entries (update/create/delete).
     */
    @Override
    @Transactional
    public NutritionDaySaveResult saveNutritionDay(NutritionDay nutritionDay) {
        List<FoodEntry> entries = nutritionDay.entries();
        int totalCalories = entries.stream().mapToInt(FoodEntry::calories).sum();
        double totalProtein = entries.stream().mapToDouble(FoodEntry::protein).sum();
        double totalFat = entries.stream().mapToDouble(FoodEntry::fat).sum();
        double totalCarbs = entries.stream().mapToDouble(FoodEntry::carbohydrate).sum();
        String summaryHash = computeSummaryHash(
                nutritionDay.userId(),
                nutritionDay.date(),
                totalCalories,
                totalProtein,
                totalFat,
                totalCarbs
        );
        String entriesHash = computeEntriesHashForDay(nutritionDay);

        // 1. Find an existing day.
        FatsecretJpaDay jpaDay = dayRepository
                .findByUserIdAndDate(nutritionDay.userId(), nutritionDay.date())
                .orElse(null);

        if (jpaDay != null) {
            // 2. If the hash is unchanged, skip the write.
            String currentEntriesHash = Optional.ofNullable(jpaDay.getEntriesHash())
                    .orElse(jpaDay.getExternalHash());
            if (entriesHash.equals(currentEntriesHash)) {
                log.debug("Day {} unchanged, skipping sync", nutritionDay.date());
                return new NutritionDaySaveResult(
                        nutritionDay.userId(),
                        nutritionDay.date(),
                        false,
                        summaryHash,
                        entriesHash,
                        totalCalories,
                        totalProtein,
                        totalFat,
                        totalCarbs
                );
            }
            // 3. Delete old entries with one DELETE.
            foodEntryRepository.deleteByDayId(jpaDay.getId());
        } else {
            jpaDay = new FatsecretJpaDay();
            jpaDay.setUserId(nutritionDay.userId());
            jpaDay.setDate(nutritionDay.date());
            jpaDay = dayRepository.save(jpaDay); // Needed for the FK.
        }

        // 4. Update day aggregates.
        jpaDay.setCalories(totalCalories);
        jpaDay.setProtein(totalProtein);
        jpaDay.setFat(totalFat);
        jpaDay.setCarbohydrate(totalCarbs);
        jpaDay.setDateInt((int) nutritionDay.date().toEpochDay());
        jpaDay.setSummaryHash(summaryHash);
        jpaDay.setEntriesHash(entriesHash);
        jpaDay.setExternalHash(entriesHash);
        jpaDay.setLastSyncAt(clock.instant());
        dayRepository.save(jpaDay);

        // 5. Map and batch-insert new entries.
        final FatsecretJpaDay finalDay = jpaDay;
        List<FatsecretFoodEntry> newEntries = entries.stream()
                .map(e -> mapDomainToEntity(e, finalDay))
                .collect(Collectors.toList());

        foodEntryRepository.saveAll(newEntries); // Hibernate batches by 50.
        return new NutritionDaySaveResult(
                nutritionDay.userId(),
                nutritionDay.date(),
                true,
                summaryHash,
                entriesHash,
                totalCalories,
                totalProtein,
                totalFat,
                totalCarbs
        );
    }

    private FatsecretFoodEntry mapDomainToEntity(FoodEntry domain, FatsecretJpaDay day) {
        FatsecretFoodEntry entity = new FatsecretFoodEntry();
        entity.setExternalEntryId(domain.externalEntryId());
        entity.setExternalFoodId(domain.externalFoodId());
        entity.setName(domain.name());
        entity.setMealType(domain.mealType());
        entity.setCalories(domain.calories());
        entity.setProtein(domain.protein());
        entity.setFat(domain.fat());
        entity.setCarbohydrate(domain.carbohydrate());
        entity.setDay(day); // Attach to the day side of the bidirectional relation.
        return entity;
    }

    @Override
    @Transactional
    public NutritionMonthSaveResult saveNutritionMonth(NutritionMonth nutritionMonth) {
        Long userId = nutritionMonth.userId();
        List<LocalDate> dates = nutritionMonth.days().stream()
                .map(NutritionDaySummary::date)
                .toList();

        if (dates.isEmpty()) {
            return new NutritionMonthSaveResult(userId, List.of());
        }

        // 1. One SELECT for the whole month.
        Map<LocalDate, FatsecretJpaDay> existingByDate = dayRepository
                .findByUserIdAndDateIn(userId, dates)
                .stream()
                .collect(Collectors.toMap(FatsecretJpaDay::getDate, Function.identity()));

        List<FatsecretJpaDay> toSave = new ArrayList<>();
        List<LocalDate> changedDates = new ArrayList<>();
        List<NutritionDaySaveResult> changedDays = new ArrayList<>();

        for (NutritionDaySummary s : nutritionMonth.days()) {
            String hash = computeSummaryHash(userId, s.date(), s.calories(), s.protein(), s.fat(), s.carbohydrate());
            FatsecretJpaDay day = existingByDate.get(s.date());

            if (day != null) {
                // Hash matches, so the data did not change.
                String currentSummaryHash = Optional.ofNullable(day.getSummaryHash())
                        .orElse(day.getExternalHash());
                if (hash.equals(currentSummaryHash)) continue;
                updateDayFromSummary(day, s, hash);
            } else {
                day = buildDayFromSummary(userId, s, hash);
            }
            changedDates.add(s.date());
            changedDays.add(new NutritionDaySaveResult(
                    userId,
                    s.date(),
                    true,
                    day.getSummaryHash(),
                    Optional.ofNullable(day.getEntriesHash()).orElse(day.getExternalHash()),
                    (int) day.getCalories(),
                    day.getProtein(),
                    day.getFat(),
                    day.getCarbohydrate()));
            toSave.add(day);
        }

        if (!toSave.isEmpty()) {
            dayRepository.saveAll(toSave); // Batch INSERT/UPDATE.
        }
        return new NutritionMonthSaveResult(userId, List.copyOf(changedDates), List.copyOf(changedDays));
    }

    @Override
    @Transactional
    public List<NutritionDaySaveResult> deleteNutritionDaysMissingFromMonth(
            Long userId,
            LocalDate monthStart,
            LocalDate monthEnd,
            Set<LocalDate> presentDates) {
        List<FatsecretJpaDay> daysToDelete = dayRepository.findByUserIdAndDateBetweenOrderByDate(
                        userId, monthStart, monthEnd)
                .stream()
                .filter(day -> !presentDates.contains(day.getDate()))
                .toList();

        if (daysToDelete.isEmpty()) {
            return List.of();
        }

        List<NutritionDaySaveResult> deletedResults = daysToDelete.stream()
                .map(day -> deletedDayResult(userId, day.getDate()))
                .toList();
        dayRepository.deleteAll(daysToDelete);
        return deletedResults;
    }

    private void updateDayFromSummary(FatsecretJpaDay day, NutritionDaySummary s, String hash) {
        day.setCalories(s.calories());
        day.setProtein(s.protein());
        day.setFat(s.fat());
        day.setCarbohydrate(s.carbohydrate());
        day.setDateInt(s.dateInt());
        day.setSummaryHash(hash);
        if (day.getEntriesHash() == null) {
            day.setExternalHash(hash);
        }
        day.setLastSyncAt(clock.instant());
    }

    private FatsecretJpaDay buildDayFromSummary(Long userId, NutritionDaySummary s, String hash) {
        FatsecretJpaDay day = new FatsecretJpaDay();
        day.setUserId(userId);
        day.setDate(s.date());
        day.setDateInt(s.dateInt());
        day.setCalories(s.calories());
        day.setProtein(s.protein());
        day.setFat(s.fat());
        day.setCarbohydrate(s.carbohydrate());
        day.setSummaryHash(hash);
        day.setExternalHash(hash);
        day.setLastSyncAt(clock.instant());
        return day;
    }

    private String computeEntriesHashForDay(NutritionDay day) {
        // Compact deterministic representation of the entry list.
        String payload = day.entries().stream()
                .sorted(Comparator.comparing(fe -> Optional.ofNullable(fe.externalEntryId())
                        .map(String::valueOf).orElse(fe.name())))
                .map(e -> String.format(Locale.ROOT, "%s|%s|%s|%s|%d|%.4f|%.4f|%.4f",
                        Optional.ofNullable(e.externalEntryId()).map(String::valueOf).orElse("null"),
                        Optional.ofNullable(e.externalFoodId()).map(String::valueOf).orElse("null"),
                        e.name(),
                        Optional.ofNullable(e.mealType()).orElse("null"),
                        e.calories(),
                        e.protein(),
                        e.fat(),
                        e.carbohydrate()))
                .collect(Collectors.joining(";"));
        return DigestUtils.sha256Hex(payload);
    }

    private String computeSummaryHash(
            Long userId,
            LocalDate date,
            double calories,
            double protein,
            double fat,
            double carbohydrate) {
        String payload = String.format(Locale.ROOT, "%d|%d|%.4f|%.4f|%.4f|%.4f",
                userId, date.toEpochDay(), calories, protein, fat, carbohydrate);
        return DigestUtils.sha256Hex(payload);
    }

    private NutritionDaySaveResult deletedDayResult(Long userId, LocalDate date) {
        String summaryHash = computeSummaryHash(userId, date, 0, 0, 0, 0);
        String entriesHash = DigestUtils.sha256Hex(String.format(Locale.ROOT, "%d|%d|deleted", userId, date.toEpochDay()));
        return new NutritionDaySaveResult(userId, date, true, summaryHash, entriesHash, 0, 0, 0, 0);
    }
}
