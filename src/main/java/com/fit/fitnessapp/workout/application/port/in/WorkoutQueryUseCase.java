package com.fit.fitnessapp.workout.application.port.in;


import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryDto;
import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryWeeklyDto;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkoutQueryUseCase {
    //in future will add more methods, base for now
    List<WorkoutSummaryDto> getAllWorkoutSummaryByUser(Long userId);
    List<WorkoutSummaryWeeklyDto> getAllWorkoutSummaryThisWeek(Long userId);
    List<WorkoutSummaryWeeklyDto> getWorkoutSummaryLastTwoWeeks(Long userId);
    List<WorkoutSummaryWeeklyDto> getWorkoutSummaryThisMonth(Long userId);
}
