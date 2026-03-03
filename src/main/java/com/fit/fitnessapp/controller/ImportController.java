package com.fit.fitnessapp.controller;

import com.fit.fitnessapp.service.workout.JefitImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final JefitImportService importService;

    @PostMapping("/jefit")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        importService.importJefitFile(file);
        return ResponseEntity.ok("Файл успешно обработан!");
    }
}
