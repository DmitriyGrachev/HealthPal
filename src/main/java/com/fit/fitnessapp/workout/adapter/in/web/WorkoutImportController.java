package com.fit.fitnessapp.workout.adapter.in.web;

import com.fit.fitnessapp.security.CurrentUserService;
import com.fit.fitnessapp.workout.application.port.in.ImportWorkoutUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/workout-import")
@RequiredArgsConstructor
public class WorkoutImportController {

    private final ImportWorkoutUseCase importUseCase;
    private final CurrentUserService currentUserService;

    @PostMapping("/import/{format}")
    public ResponseEntity<String> importFile(
            @PathVariable String format,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            Long userId = currentUserService.getCurrentUserId();

            // Вызываем бизнес-логику через порт. Передаем чистый поток!
            importUseCase.importWorkouts(file.getInputStream(), format, userId);

            return ResponseEntity.ok("Тренировки формата " + format + " успешно импортированы!");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Ошибка: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Ошибка парсинга файла: " + e.getMessage());
        }
    }
}