package com.fit.fitnessapp.workout.adapter.in.web;

import com.fit.fitnessapp.security.CurrentUserService;
import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryWeeklyDto;
import com.fit.fitnessapp.workout.application.port.in.WorkoutQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workout-analitic")
@RequiredArgsConstructor
public class WorkoutAnalyticController {

    private final WorkoutQueryUseCase workoutQueryUseCase;
    private final CurrentUserService currentUserService;

    private enum SUMMARY_PERIOD {
        THIS_WEEK, LAST_TWO_WEEKS, THIS_MONTH
    }

    @GetMapping("/summary")
    public List<WorkoutSummaryWeeklyDto> getAllWorkoutSummaryThisWeek(
            @RequestParam(defaultValue = "THIS_WEEK") SUMMARY_PERIOD period)
    {
        return switch (period){
            case THIS_WEEK -> workoutQueryUseCase.getAllWorkoutSummaryThisWeek(currentUserService.getCurrentUserId());
            case LAST_TWO_WEEKS -> workoutQueryUseCase.getWorkoutSummaryLastTwoWeeks(currentUserService.getCurrentUserId());
            case THIS_MONTH ->  workoutQueryUseCase.getWorkoutSummaryThisMonth(currentUserService.getCurrentUserId());
        };

    }
}

