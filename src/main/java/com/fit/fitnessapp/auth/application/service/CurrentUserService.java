package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.application.port.out.UserAuthenticationPort;
import com.fit.fitnessapp.auth.domain.CurrentUserView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService implements CurrentUserApi {

    private final UserAuthenticationPort userAuthenticationPort;

    private CurrentUserView getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            if (username != null && !username.isBlank() && !"anonymousUser".equals(username)) {
                return userAuthenticationPort.findCurrentUserByUsername(username);
            }
        }

        throw new IllegalStateException("User is not authenticated");
    }

    @Override
    public Long getCurrentUserId() {
        return getCurrentUser().id();
    }

    @Override
    public String getCurrentUserEmail() {
        return getCurrentUser().email();
    }

    @Override
    public void findUserById(Long userId) {
        userAuthenticationPort.requireUserExists(userId);
    }
}
