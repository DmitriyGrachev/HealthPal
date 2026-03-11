package com.fit.fitnessapp.nutrition.domain;

public record FatSecretAuthResult(
        Long userId,
        FatSecretToken token
) {}