package com.fit.fitnessapp.workout.application.port.out;

import com.fit.fitnessapp.workout.domain.WorkoutSession;

import java.io.InputStream;
import java.util.List;

public interface WorkoutParserPort {
    boolean supports(String format);
    List<WorkoutSession> parse(InputStream inputStream);
}
