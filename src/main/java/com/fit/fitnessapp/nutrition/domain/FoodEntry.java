package com.fit.fitnessapp.nutrition.domain;

public record FoodEntry(
        Long externalFoodId,
        Long externalEntryId,
        String name,
        String mealType, // Breakfast, Lunch, etc.
        int calories,
        double protein,
        double fat,
        double carbohydrate
) {}
