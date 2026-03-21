package com.fit.fitnessapp.workout.application.port.in;


import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryDto;

import java.util.List;

public interface WorkoutQueryUseCase {
    //in future will add more methods, base for now
    List<WorkoutSummaryDto> getAllWorkoutSummaryByUser(Long userId);
}
