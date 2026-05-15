package com.fit.fitnessapp.auth.api;

import java.util.List;

public interface UserPort {
    List<Long> getUserIdsWithFatSecretTokens();
    String getFatSecretAccessTokenByUserId(Long userId);
    String getFatSecretAccessTokenSecretByUserId(Long userId);
}
