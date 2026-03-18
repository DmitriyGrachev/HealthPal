package com.fit.fitnessapp.nutrition.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity
@Table(name = "fatsecret_food", indexes = {
        @Index(name = "idx_external_entry_id", columnList = "external_entry_id")
})
@Getter @Setter
public class FatsecretFoodEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fatsecret_food_seq")
    @SequenceGenerator(name = "fatsecret_food_seq", sequenceName = "fatsecret_food_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "external_food_id")
    private Long externalFoodId;

    @Column(name = "external_entry_id", unique = false) // unique не глобально — уникальность в пределах day
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
