package com.fit.fitnessapp.auth.application.port.out;

import com.fit.fitnessapp.auth.domain.CurrentUserView;
import org.springframework.security.core.userdetails.UserDetails;

public interface UserAuthenticationPort {

    UserDetails loadUserByUsername(String username);

    CurrentUserView findCurrentUserByUsername(String username);

    void requireUserExists(Long userId);
}
