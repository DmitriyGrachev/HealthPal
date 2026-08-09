package com.fit.fitnessapp.workout.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.workout.application.port.in.ImportWorkoutUseCase;
import com.fit.fitnessapp.workout.domain.WorkoutImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/workout-import")
@RequiredArgsConstructor
public class WorkoutImportController {

    private final ImportWorkoutUseCase importUseCase;
    private final CurrentUserApi currentUserService;

    @PostMapping("/import/{format}")
    public ResponseEntity<WorkoutImportResponse> importFile(
            @PathVariable String format,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        Long userId = currentUserService.getCurrentUserId();
        WorkoutImportResult result = importUseCase.importWorkouts(file.getInputStream(), format, userId);
        return ResponseEntity.ok(new WorkoutImportResponse(
                "completed",
                format,
                result.importedCount(),
                result.changedCount(),
                result.skippedCount(),
                result.warnings()));
    }
}
