package com.fit.fitnessapp.auth;

import java.util.Optional;

public interface FatSecretTokenUseCase {
    void saveToken(Long userId, String accessToken, String accessTokenSecret);
    Optional<FatSecretTokenData> getToken(Long userId);
    
    record FatSecretTokenData(String accessToken, String accessTokenSecret) {}
}
