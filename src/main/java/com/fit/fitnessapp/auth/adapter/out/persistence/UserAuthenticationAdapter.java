package com.fit.fitnessapp.auth.adapter.out.persistence;

import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
import com.fit.fitnessapp.auth.application.port.out.UserAuthenticationPort;
import com.fit.fitnessapp.auth.domain.CurrentUserView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserAuthenticationAdapter implements UserAuthenticationPort {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Override
    public CurrentUserView findCurrentUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> new CurrentUserView(user.getId(), user.getEmail()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Override
    public void requireUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UsernameNotFoundException("User not found: " + userId);
        }
    }
}
