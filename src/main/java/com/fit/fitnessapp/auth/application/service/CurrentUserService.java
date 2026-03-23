package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserService implements CurrentUserApi {

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();

            if (principal instanceof User) {
                return (User) principal;
            }
        }
        throw new RuntimeException("Пользователь не найден в контексте (возможно, не залогинен)");
    }


    // Сокращенный метод, если нужен только ID
    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    @Override
    public String getCurrentUserEmail() {
        return getCurrentUser().getEmail();
    }
}
