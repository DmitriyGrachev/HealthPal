package com.fit.fitnessapp.model.fatsecret;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "fatsecret_food")
public class FatsecretFoodEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    int calories;
    double carbohydrate;
    int date_int; //20488",
    double fat;
    String food_entry_description; //": "2 :custom:200s  мл Селянське Молоко 1,5%",
    long food_entry_id; //22417515131",
    String food_entry_name;
    long food_id;
    String meal; //": "Breakfast",
    double number_of_units;
    double protein;
    long serving_id;
    double sugar;
}
