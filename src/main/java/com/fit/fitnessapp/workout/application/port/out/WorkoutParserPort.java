package com.fit.fitnessapp.workout.application.port.out;

import com.fit.fitnessapp.workout.domain.WorkoutImportResult;

import java.io.InputStream;

public interface WorkoutParserPort {
    boolean supports(String format);
    WorkoutImportResult parse(InputStream inputStream);
}
