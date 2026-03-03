package com.fit.fitnessapp.repository;

import com.fit.fitnessapp.model.workout.Workout;
import com.fit.fitnessapp.model.workout.WorkoutExercise;
import jdk.jfr.Registered;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkoutRepository extends JpaRepository<Workout, Workout> {
}
