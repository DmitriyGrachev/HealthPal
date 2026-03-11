package com.fit.fitnessapp.nutrition.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "fatsecret_food")
public class FatsecretFoodEntry { // [cite: 21]
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long externalFoodId;
    private Long externalEntryId;
    private String name;
    private String mealType;
    private int calories;
    private double protein;
    private double fat;
    private double carbohydrate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id")
    private FatsecretJpaDay day;
}
