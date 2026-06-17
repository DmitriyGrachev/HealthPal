package com.fit.fitnessapp.auth.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(max = 64)
        String username,

        @NotBlank
        @Size(min = 8, max = 128)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$",
                message = "password must contain upper and lower case letters, a digit, and a special character")
        String password,

        @NotBlank
        @Email
        @Size(max = 255)
        String email) {
}
