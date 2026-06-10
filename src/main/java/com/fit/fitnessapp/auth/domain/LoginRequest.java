package com.fit.fitnessapp.auth.domain;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest (
        @NotBlank
        String username,

        @NotBlank
        String password){
}
