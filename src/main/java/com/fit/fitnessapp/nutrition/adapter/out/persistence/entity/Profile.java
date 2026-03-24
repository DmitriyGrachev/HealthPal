package com.fit.fitnessapp.nutrition.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "profile")
@Getter
@Setter
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "goal_weight_kg")
    private Double goalWeightKg;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "height_measure", length = 20)
    private String heightMeasure;

    @Column(name = "last_weight_date_int")
    private Integer lastWeightDateInt;

    @Column(name = "last_weight_kg")
    private Double lastWeightKg;

    @Column(name = "weight_measure", length = 20)
    private String weightMeasure;
}
