package com.fit.fitnessapp.nutrition.adapter.out.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "fatsecret_food", indexes = {
        @Index(name = "idx_external_entry_id", columnList = "external_entry_id")
})
public class FatsecretFoodEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fatsecret_food_seq")
    @SequenceGenerator(name = "fatsecret_food_seq", sequenceName = "fatsecret_food_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "external_food_id")
    private Long externalFoodId;

    @Column(name = "external_entry_id", unique = false)
    private Long externalEntryId;

    private String name;
    private String mealType;
    private int calories;
    private double protein;
    private double fat;
    private double carbohydrate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    private FatsecretJpaDay day;

    public FatsecretFoodEntry() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getExternalFoodId() { return externalFoodId; }
    public void setExternalFoodId(Long externalFoodId) { this.externalFoodId = externalFoodId; }

    public Long getExternalEntryId() { return externalEntryId; }
    public void setExternalEntryId(Long externalEntryId) { this.externalEntryId = externalEntryId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMealType() { return mealType; }
    public void setMealType(String mealType) { this.mealType = mealType; }

    public int getCalories() { return calories; }
    public void setCalories(int calories) { this.calories = calories; }

    public double getProtein() { return protein; }
    public void setProtein(double protein) { this.protein = protein; }

    public double getFat() { return fat; }
    public void setFat(double fat) { this.fat = fat; }

    public double getCarbohydrate() { return carbohydrate; }
    public void setCarbohydrate(double carbohydrate) { this.carbohydrate = carbohydrate; }

    public FatsecretJpaDay getDay() { return day; }
    public void setDay(FatsecretJpaDay day) { this.day = day; }
}
