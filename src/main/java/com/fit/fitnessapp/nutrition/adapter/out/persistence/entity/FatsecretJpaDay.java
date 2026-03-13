package com.fit.fitnessapp.nutrition.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "fatsecret_day")
public class FatsecretJpaDay { // [cite: 10]
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private LocalDate date;
    private int dateInt;
    private double calories;
    private double protein;
    private double fat;
    private double carbohydrate;

    // Ключевой момент для обновления списков!
    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FatsecretFoodEntry> entries = new ArrayList<>();

    // Вспомогательный метод для синхронизации двунаправленной связи
    public void addEntry(FatsecretFoodEntry entry) {
        entries.add(entry);
        entry.setDay(this);
    }
}