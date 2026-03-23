package com.fit.fitnessapp.auth;

public interface CurrentUserApi {
    Long getCurrentUserId();
    String getCurrentUserEmail();
    void findUserById (Long userId);
}
