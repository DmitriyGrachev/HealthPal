package com.fit.fitnessapp.nutrition.adapter.out.persistence;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatSecretConnectionJpaEntity;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatsecretFoodEntry;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatsecretJpaDay;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatSecretConnectionJpaRepository;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatsecretDayJpaRepository;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatsecretFoodEntryJpaRepository;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionPersistencePort;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NutritionPersistenceAdapter implements NutritionCommandPort {

    private final FatSecretConnectionJpaRepository connectionRepository;
    private final FatsecretDayJpaRepository dayRepository;
    private final FatsecretFoodEntryJpaRepository foodEntryRepository;

    @Override
    public void saveToken(Long userId, FatSecretToken token) {
        FatSecretConnectionJpaEntity entity = connectionRepository.findByUserId(userId)
                .orElseGet(() -> {
                    FatSecretConnectionJpaEntity newEntity = new FatSecretConnectionJpaEntity();
                    newEntity.setUserId(userId);
                    return newEntity;
                });

        entity.setAccessToken(token.accessToken());
        entity.setAccessTokenSecret(token.accessTokenSecret());

        connectionRepository.save(entity);
    }

    @Override
    public Optional<FatSecretToken> getToken(Long userId) {
        return connectionRepository.findByUserId(userId)
                .map(entity -> new FatSecretToken(entity.getAccessToken(), entity.getAccessTokenSecret()));
    }

    /**
     * Идемпотентный upsert полного дня: обновляет агрегаты, синхронизирует записи (update/create/delete).
     */
    @Override
    @Transactional
    public void saveNutritionDay(NutritionDay nutritionDay) {
        String incomingHash = computeHashForDay(nutritionDay);

        // 1. Ищем существующий день
        FatsecretJpaDay jpaDay = dayRepository
                .findByUserIdAndDate(nutritionDay.userId(), nutritionDay.date())
                .orElse(null);

        if (jpaDay != null) {
            // 2. Если хэш не изменился — ничего не делаем
            if (incomingHash.equals(jpaDay.getExternalHash())) {
                log.debug("Day {} unchanged, skipping sync", nutritionDay.date());
                return;
            }
            // 3. Удаляем старые записи одним DELETE
            foodEntryRepository.deleteByDayId(jpaDay.getId());
        } else {
            jpaDay = new FatsecretJpaDay();
            jpaDay.setUserId(nutritionDay.userId());
            jpaDay.setDate(nutritionDay.date());
            jpaDay = dayRepository.save(jpaDay); // нужен id для FK
        }

        // 4. Обновляем агрегаты дня
        List<FoodEntry> entries = nutritionDay.entries();
        jpaDay.setCalories(entries.stream().mapToInt(FoodEntry::calories).sum());
        jpaDay.setProtein(entries.stream().mapToDouble(FoodEntry::protein).sum());
        jpaDay.setFat(entries.stream().mapToDouble(FoodEntry::fat).sum());
        jpaDay.setCarbohydrate(entries.stream().mapToDouble(FoodEntry::carbohydrate).sum());
        jpaDay.setExternalHash(incomingHash);
        jpaDay.setLastSyncAt(Instant.now());
        dayRepository.save(jpaDay);

        // 5. Маппим и батч-вставляем новые записи
        final FatsecretJpaDay finalDay = jpaDay;
        List<FatsecretFoodEntry> newEntries = entries.stream()
                .map(e -> mapDomainToEntity(e, finalDay))
                .collect(Collectors.toList());

        foodEntryRepository.saveAll(newEntries); // Hibernate батчит по 50
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
        entity.setDay(day); // Привязываем к дню (Bidirectional связь)
        return entity;
    }

    @Override
    @Transactional
    public void saveNutritionMonth(NutritionMonth nutritionMonth) {
        Long userId = nutritionMonth.userId();
        List<LocalDate> dates = nutritionMonth.days().stream()
                .map(NutritionDaySummary::date)
                .toList();

        // 1. Один SELECT на весь месяц
        Map<LocalDate, FatsecretJpaDay> existingByDate = dayRepository
                .findByUserIdAndDateIn(userId, dates)
                .stream()
                .collect(Collectors.toMap(FatsecretJpaDay::getDate, Function.identity()));

        List<FatsecretJpaDay> toSave = new ArrayList<>();

        for (NutritionDaySummary s : nutritionMonth.days()) {
            String hash = computeHashForSummary(s);
            FatsecretJpaDay day = existingByDate.get(s.date());

            if (day != null) {
                // Хэш совпадает — данные не изменились, пропускаем
                if (hash.equals(day.getExternalHash())) continue;
                updateDayFromSummary(day, s, hash);
            } else {
                day = buildDayFromSummary(userId, s, hash);
            }
            toSave.add(day);
        }

        dayRepository.saveAll(toSave); // батч INSERT/UPDATE
    }

    private void updateDayFromSummary(FatsecretJpaDay day, NutritionDaySummary s, String hash) {
        day.setCalories(s.calories());
        day.setProtein(s.protein());
        day.setFat(s.fat());
        day.setCarbohydrate(s.carbohydrate());
        day.setDateInt(s.dateInt());
        day.setExternalHash(hash);
        day.setLastSyncAt(Instant.now());
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
        day.setExternalHash(hash);
        day.setLastSyncAt(Instant.now());
        return day;
    }

    private String computeHashForDay(NutritionDay day) {
        // Компактное детерминированное представление списка записей
        String payload = day.entries().stream()
                .sorted(Comparator.comparing(fe -> Optional.ofNullable(fe.externalEntryId())
                        .map(String::valueOf).orElse(fe.name())))
                .map(e -> String.format("%s|%s|%d|%s",
                        Optional.ofNullable(e.externalEntryId()).map(String::valueOf).orElse("null"),
                        e.name(),
                        e.calories(),
                        Optional.ofNullable(e.mealType()).orElse("null")))
                .collect(Collectors.joining(";"));
        return DigestUtils.sha256Hex(payload);
    }

    private String computeHashForSummary(NutritionDaySummary s) {
        String payload = s.userId() + "|" + s.dateInt() + "|" + s.calories() + "|" + s.protein() + "|" + s.fat() + "|" + s.carbohydrate();
        return DigestUtils.sha256Hex(payload);
    }

    // Добавить приватный маппер entity → domain
    private FoodEntry toDomainEntry(FatsecretFoodEntry e) {
        return new FoodEntry(
                e.getExternalFoodId(),
                e.getExternalEntryId(),
                e.getName(),
                e.getMealType(),
                e.getCalories(),
                e.getProtein(),
                e.getFat(),
                e.getCarbohydrate()
        );
    }
}