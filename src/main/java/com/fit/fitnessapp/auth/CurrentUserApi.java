package com.fit.fitnessapp.auth;

public interface CurrentUserApi {
    Long getCurrentUserId();
    // Если кому-то понадобится email (например, для нотификаций)
    String getCurrentUserEmail();
}
