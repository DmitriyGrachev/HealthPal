package com.fit.fitnessapp.nutrition.adapter.out.persistence;

import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatSecretConnectionJpaEntity;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatsecretFoodEntry;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.entity.FatsecretJpaDay;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatSecretConnectionJpaRepository;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.repository.FatsecretDayJpaRepository;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionPersistencePort;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NutritionPersistenceAdapter implements NutritionPersistencePort {

    private final FatSecretConnectionJpaRepository connectionRepository;
    private final FatsecretDayJpaRepository dayRepository;

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
        // 1. Ищем существующий день в БД
        FatsecretJpaDay jpaDay = dayRepository.findByUserIdAndDate(nutritionDay.userId(), nutritionDay.date())
                .orElseGet(() -> {
                    FatsecretJpaDay newDay = new FatsecretJpaDay();
                    newDay.setUserId(nutritionDay.userId());
                    newDay.setDate(nutritionDay.date());
                    return newDay;
                });

        // 2. Мапим доменные записи в JPA сущности
        List<FatsecretFoodEntry> newEntries = nutritionDay.entries().stream()
                .map(entry -> mapDomainToEntity(entry, jpaDay))
                .collect(Collectors.toList());

        // 3. Очищаем старые записи и добавляем новые.
        // Важно: в FatsecretJpaDay коллекция должна иметь orphanRemoval = true
        if (jpaDay.getEntries() != null) {
            jpaDay.getEntries().clear();
            jpaDay.getEntries().addAll(newEntries);
        } else {
            jpaDay.setEntries(newEntries);
        }

        // 4. Сохраняем (JPA сам сделает INSERT или UPDATE)
        dayRepository.save(jpaDay);
    }

    // Остальные методы интерфейса...

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

    /**
     * Сохранение месячных summary: просто апдейт/создание агрегированных дней без деталей.
     * Политика: мы не удаляем существующие дни, которых нет в summary (см. рекомендации).
     */
    @Override
    @Transactional
    public void saveNutritionMonth(NutritionMonth nutritionMonth) {
        Long userId = nutritionMonth.userId();
        for (NutritionDaySummary s : nutritionMonth.days()) {
            LocalDate date = s.date();
            FatsecretJpaDay existing = dayRepository.findByUserIdAndDate(userId, date).orElse(null);

            String summaryHash = computeHashForSummary(s);

            if (existing != null) {
                // Апдейт агрегатов. Не трогаем детальные записи.
                existing.setCalories((int) Math.round(s.calories()));
                existing.setProtein(s.protein());
                existing.setFat(s.fat());
                existing.setCarbohydrate(s.carbohydrate());
                existing.setDateInt(s.dateInt());
                existing.setExternalHash(summaryHash); // помечаем, что summary обновлён
                existing.setLastSyncAt(Instant.now());
                dayRepository.save(existing);
            } else {
                FatsecretJpaDay jpa = new FatsecretJpaDay();
                jpa.setUserId(userId);
                jpa.setDate(s.date());
                jpa.setDateInt(s.dateInt());
                jpa.setCalories((int) Math.round(s.calories()));
                jpa.setProtein(s.protein());
                jpa.setFat(s.fat());
                jpa.setCarbohydrate(s.carbohydrate());
                jpa.setExternalHash(summaryHash);
                jpa.setLastSyncAt(Instant.now());
                dayRepository.save(jpa);
            }
        }
    }

    // ----------------- Helpers -----------------

    private FatsecretFoodEntry mapDomainToEntity(FoodEntry e) {
        FatsecretFoodEntry ent = new FatsecretFoodEntry();
        ent.setExternalFoodId(e.externalFoodId());
        ent.setExternalEntryId(e.externalEntryId());
        ent.setName(e.name());
        ent.setMealType(e.mealType());
        ent.setCalories(e.calories());
        ent.setProtein(e.protein());
        ent.setFat(e.fat());
        ent.setCarbohydrate(e.carbohydrate());
        return ent;
    }

    private void updateEntityFromDomain(FatsecretFoodEntry ent, FoodEntry src) {
        ent.setExternalFoodId(src.externalFoodId());
        ent.setName(src.name());
        ent.setMealType(src.mealType());
        ent.setCalories(src.calories());
        ent.setProtein(src.protein());
        ent.setFat(src.fat());
        ent.setCarbohydrate(src.carbohydrate());
        // externalEntryId обычно стабильный; если пришёл другой — обновим
        ent.setExternalEntryId(src.externalEntryId());
    }

    private void syncEntries(FatsecretJpaDay existing, List<FoodEntry> incoming) {
        // map existing by externalEntryId (если есть)
        Map<Long, FatsecretFoodEntry> existingByExtId = existing.getEntries().stream()
                .filter(e -> e.getExternalEntryId() != null)
                .collect(Collectors.toMap(FatsecretFoodEntry::getExternalEntryId, Function.identity()));

        // helper: map incoming by external id and compute composite keys for those without external id
        Set<Long> incomingExtIds = incoming.stream()
                .map(FoodEntry::externalEntryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // For matching entries without external id, use composite key: name|meal|calories
        Map<String, FoodEntry> incomingByComposite = incoming.stream()
                .filter(e -> e.externalEntryId() == null)
                .collect(Collectors.toMap(this::compositeKeyForDomain, Function.identity(), (a, b) -> a));

        // Обновляем или создаём
        for (FoodEntry inc : incoming) {
            if (inc.externalEntryId() != null && existingByExtId.containsKey(inc.externalEntryId())) {
                FatsecretFoodEntry existEnt = existingByExtId.get(inc.externalEntryId());
                updateEntityFromDomain(existEnt, inc);
            } else {
                // Try match by composite key (only when externalEntryId is null)
                if (inc.externalEntryId() == null) {
                    Optional<FatsecretFoodEntry> match = existing.getEntries().stream()
                            .filter(ent -> ent.getExternalEntryId() == null)
                            .filter(ent -> compositeKeyForEntity(ent).equals(compositeKeyForDomain(inc)))
                            .findFirst();
                    if (match.isPresent()) {
                        updateEntityFromDomain(match.get(), inc);
                        continue;
                    }
                }
                // иначе — новая запись
                FatsecretFoodEntry newEnt = mapDomainToEntity(inc);
                existing.addEntry(newEnt);
            }
        }

        // Удаляем те существующие записи, которые не находятся во входном наборе (по external id или composite)
        Iterator<FatsecretFoodEntry> it = existing.getEntries().iterator();
        while (it.hasNext()) {
            FatsecretFoodEntry ent = it.next();
            Long extId = ent.getExternalEntryId();
            boolean shouldKeep = false;
            if (extId != null) {
                shouldKeep = incomingExtIds.contains(extId);
            } else {
                String comp = compositeKeyForEntity(ent);
                shouldKeep = incomingByComposite.containsKey(comp);
            }
            if (!shouldKeep) {
                it.remove();
                ent.setDay(null); // orphanRemoval = true -> удалится
            }
        }
    }

    private String compositeKeyForDomain(FoodEntry e) {
        String name = Optional.ofNullable(e.name()).orElse("").trim().toLowerCase();
        String meal = Optional.ofNullable(e.mealType()).orElse("").trim().toLowerCase();
        return name + "|" + meal + "|" + e.calories();
    }

    private String compositeKeyForEntity(FatsecretFoodEntry e) {
        String name = Optional.ofNullable(e.getName()).orElse("").trim().toLowerCase();
        String meal = Optional.ofNullable(e.getMealType()).orElse("").trim().toLowerCase();
        return name + "|" + meal + "|" + e.getCalories();
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

    private int sumProtein(NutritionDay day) {
        return (int) Math.round(day.entries().stream().mapToDouble(FoodEntry::protein).sum());
    }

    private int sumFat(NutritionDay day) {
        return (int) Math.round(day.entries().stream().mapToDouble(FoodEntry::fat).sum());
    }

    private int sumCarbs(NutritionDay day) {
        return (int) Math.round(day.entries().stream().mapToDouble(FoodEntry::carbohydrate).sum());
    }
    @Override
    public List<Long> getAllConnectedUserIds() {
        return connectionRepository.findAll()
                .stream()
                .map(FatSecretConnectionJpaEntity::getUserId)
                .toList();
    }

    @Override
    public Optional<NutritionDay> getDayByDate(Long userId, LocalDate date) {
        return dayRepository.findByUserIdAndDate(userId, date)
                .map(jpaDay -> {
                    List<FoodEntry> entries = jpaDay.getEntries().stream()
                            .map(this::toDomainEntry)
                            .toList();
                    return new NutritionDay(userId, jpaDay.getDate(), entries);
                });
    }

    @Override
    public List<NutritionDaySummary> getMonthSummary(Long userId, LocalDate from, LocalDate to) {
        return dayRepository.findByUserIdAndDateBetweenOrderByDate(userId, from, to)
                .stream()
                .map(jpaDay -> new NutritionDaySummary(
                        userId,
                        jpaDay.getDate(),
                        jpaDay.getDateInt(),
                        jpaDay.getCalories(),
                        jpaDay.getProtein(),
                        jpaDay.getFat(),
                        jpaDay.getCarbohydrate()
                ))
                .toList();
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