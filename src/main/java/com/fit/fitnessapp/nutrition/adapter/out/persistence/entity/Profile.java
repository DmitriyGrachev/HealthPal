package com.fit.fitnessapp.nutrition.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    // Phase 1 enhancements
    @Column(name = "age")
    private Integer age;

    @Column(name = "gender")
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "primary_goal")
    @Enumerated(EnumType.STRING)
    private FitnessGoal primaryGoal;

    @Column(name = "target_weight_kg", precision = 5, scale = 2)
    private BigDecimal targetWeightKg;

    @Column(name = "target_date")
    private LocalDate targetDate;

    public enum Gender {
        MALE, FEMALE, OTHER
    }

    public enum FitnessGoal {
        WEIGHT_LOSS, MUSCLE_GAIN, MAINTENANCE, PERFORMANCE
    }
}
