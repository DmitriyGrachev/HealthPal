package com.fit.fitnessapp.auth.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.application.port.in.UserDataLifecycleUseCase;
import com.fit.fitnessapp.auth.domain.UserAccountDeletionResult;
import com.fit.fitnessapp.auth.domain.UserDataExportDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user/me")
@RequiredArgsConstructor
public class UserDataLifecycleController {

    private final UserDataLifecycleUseCase userDataLifecycleUseCase;
    private final CurrentUserApi currentUserApi;

    @GetMapping("/export")
    public ResponseEntity<UserDataExportDto> exportUserData() {
        Long currentUserId = currentUserApi.getCurrentUserId();
        UserDataExportDto exportDto = userDataLifecycleUseCase.exportUserData(currentUserId);
        return ResponseEntity.ok(exportDto);
    }

    @PostMapping("/disconnect-fatsecret")
    public ResponseEntity<Void> disconnectFatSecret() {
        Long currentUserId = currentUserApi.getCurrentUserId();
        userDataLifecycleUseCase.disconnectFatSecret(currentUserId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<UserAccountDeletionResult> deleteAccount() {
        Long currentUserId = currentUserApi.getCurrentUserId();
        UserAccountDeletionResult result = userDataLifecycleUseCase.deleteAccount(currentUserId);
        return ResponseEntity.ok(result);
    }
}
