package com.fit.fitnessapp.auth.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.UserTimeApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/me/timezone")
@RequiredArgsConstructor
public class UserTimeController {

    private final CurrentUserApi currentUserApi;
    private final UserTimeApi userTimeApi;

    @GetMapping
    public ResponseEntity<TimeZoneResponse> getTimeZone() {
        return ResponseEntity.ok(new TimeZoneResponse(userTimeApi.getTimeZone(currentUserApi.getCurrentUserId())));
    }

    @PutMapping
    public ResponseEntity<TimeZoneResponse> setTimeZone(@Valid @RequestBody TimeZoneRequest request) {
        Long userId = currentUserApi.getCurrentUserId();
        userTimeApi.setTimeZone(userId, request.ianaTimeZone());
        return ResponseEntity.ok(new TimeZoneResponse(userTimeApi.getTimeZone(userId)));
    }

    public record TimeZoneRequest(@NotBlank String ianaTimeZone) {
    }

    public record TimeZoneResponse(String ianaTimeZone) {
    }
}
