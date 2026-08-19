package com.fit.fitnessapp.auth.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.application.port.in.UserDataExportManifestUseCase;
import com.fit.fitnessapp.auth.domain.UserDataExportManifest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/user/me")
@RequiredArgsConstructor
public class UserDataExportV2Controller {

    private final UserDataExportManifestUseCase userDataExportManifestUseCase;
    private final CurrentUserApi currentUserApi;

    @GetMapping("/export")
    public ResponseEntity<UserDataExportManifest> exportUserData() {
        Long currentUserId = currentUserApi.getCurrentUserId();
        return ResponseEntity.ok(userDataExportManifestUseCase.exportUserData(currentUserId));
    }
}
