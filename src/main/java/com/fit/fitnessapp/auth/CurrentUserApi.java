package com.fit.fitnessapp.auth;

@org.springframework.modulith.NamedInterface("current-user")
public interface CurrentUserApi {
    Long getCurrentUserId();
    String getCurrentUserEmail();
    void findUserById (Long userId);
}
