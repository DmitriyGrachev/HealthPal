package com.fit.fitnessapp.nutrition.application.port.in;

public interface ConnectFatSecretUseCase {
    String getAuthorizationUrl(Long userId);
    void processCallback(String oauthToken, String oauthVerifier);
}