package com.fit.fitnessapp.nutrition.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
@Entity
@Table(name = "fatsecret_day",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "date"}))
@Getter @Setter
public class FatsecretJpaDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "date_int")
    private int dateInt;

    private double calories;
    private double protein;
    private double fat;
    private double carbohydrate;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FatsecretFoodEntry> entries = new ArrayList<>();

    @Version
    private Long version;

    // Хэш агрегированных данных для быстрого сравнения
    @Column(name = "external_hash")
    private String externalHash;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    // синхронизирующий метод двунаправленной связи
    public void addEntry(FatsecretFoodEntry entry) {
        entries.add(entry);
        entry.setDay(this);
    }

    public void removeEntry(FatsecretFoodEntry entry) {
        entries.remove(entry);
        entry.setDay(null);
    }
}