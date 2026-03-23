package com.fit.fitnessapp.auth.domain;

public record RegisterRequest(
        String username,
        String password,
        String email) {
}
