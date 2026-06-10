package com.fit.fitnessapp.auth.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(max = 64)
        String username,

        @NotBlank
        @Size(min = 6, max = 128)
        String password,

        @NotBlank
        @Email
        @Size(max = 255)
        String email) {
}
