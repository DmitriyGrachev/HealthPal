package com.fit.fitnessapp.auth.domain;

public record LoginRequest (
        String username,
        String password){
}
