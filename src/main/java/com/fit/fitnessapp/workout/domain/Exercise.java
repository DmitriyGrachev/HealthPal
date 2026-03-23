package com.fit.fitnessapp.workout.domain;

import java.util.List;

// Exercise.java
public record Exercise(Long jefitLogId, String name, List<Set> sets) {}
