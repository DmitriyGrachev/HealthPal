package com.fit.fitnessapp.security;

import com.fit.fitnessapp.model.user.User;
import org.checkerframework.checker.units.qual.A;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserService {

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();

            // Проверяем, что там лежит именно наш User, а не просто строка "anonymousUser"
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
}
