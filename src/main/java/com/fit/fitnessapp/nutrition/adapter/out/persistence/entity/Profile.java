package com.fit.fitnessapp.nutrition.adapter.out.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "profile")
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

    public Profile() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Double getGoalWeightKg() { return goalWeightKg; }
    public void setGoalWeightKg(Double goalWeightKg) { this.goalWeightKg = goalWeightKg; }

    public Double getHeightCm() { return heightCm; }
    public void setHeightCm(Double heightCm) { this.heightCm = heightCm; }

    public String getHeightMeasure() { return heightMeasure; }
    public void setHeightMeasure(String heightMeasure) { this.heightMeasure = heightMeasure; }

    public Integer getLastWeightDateInt() { return lastWeightDateInt; }
    public void setLastWeightDateInt(Integer lastWeightDateInt) { this.lastWeightDateInt = lastWeightDateInt; }

    public Double getLastWeightKg() { return lastWeightKg; }
    public void setLastWeightKg(Double lastWeightKg) { this.lastWeightKg = lastWeightKg; }

    public String getWeightMeasure() { return weightMeasure; }
    public void setWeightMeasure(String weightMeasure) { this.weightMeasure = weightMeasure; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public FitnessGoal getPrimaryGoal() { return primaryGoal; }
    public void setPrimaryGoal(FitnessGoal primaryGoal) { this.primaryGoal = primaryGoal; }

    public BigDecimal getTargetWeightKg() { return targetWeightKg; }
    public void setTargetWeightKg(BigDecimal targetWeightKg) { this.targetWeightKg = targetWeightKg; }

    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }

    public enum Gender {
        MALE, FEMALE, OTHER
    }

    public enum FitnessGoal {
        WEIGHT_LOSS, MUSCLE_GAIN, MAINTENANCE, PERFORMANCE
    }
}
